package com.example.engine

import com.example.data.model.KnockProtocol
import com.example.data.model.KnockStep
import com.example.data.model.KnockStepStatus
import com.example.data.model.LogCategory
import com.example.data.model.StepRunState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

data class ProbeResult(
    val host: String,
    val port: Int,
    val reachable: Boolean,
    val rttMs: Long,
    val detail: String
)

data class KnockSequenceResult(
    val success: Boolean,
    val resolvedIp: String,
    val totalDurationMs: Long,
    val stepStatuses: List<KnockStepStatus>
)

class PortKnocker(private val logger: DiagnosticLogger) {

    suspend fun executeKnockSequence(
        targetHost: String,
        steps: List<KnockStep>,
        onStepUpdate: (List<KnockStepStatus>) -> Unit
    ): KnockSequenceResult = withContext(Dispatchers.IO) {
        val trimmedHost = targetHost.trim()
        if (trimmedHost.isBlank() || steps.isEmpty()) {
            logger.log(
                LogCategory.KNOCK,
                "تنظیمات ناکینگ ناقص است",
                "آدرس هدف یا لیست پورتها خالی است",
                isError = true
            )
            return@withContext KnockSequenceResult(false, "", 0L, emptyList())
        }

        val startAll = System.currentTimeMillis()
        val initialStatuses = steps.mapIndexed { idx, step ->
            KnockStepStatus(idx, step, StepRunState.IDLE)
        }.toMutableList()
        onStepUpdate(initialStatuses.toList())

        val resolvedAddr = runCatching { InetAddress.getByName(trimmedHost) }.getOrNull()
        if (resolvedAddr == null) {
            logger.log(
                LogCategory.KNOCK,
                "خطا در DNS برای هدف ناک: $trimmedHost",
                "امکان تبدیل دامنه به IP وجود ندارد",
                isError = true
            )
            val failed = initialStatuses.map {
                it.copy(state = StepRunState.FAILED, note = "DNS Error")
            }
            onStepUpdate(failed)
            return@withContext KnockSequenceResult(false, "", 0L, failed)
        }

        val targetIp = resolvedAddr.hostAddress ?: trimmedHost
        logger.log(
            LogCategory.KNOCK,
            "شروع توالی ناکینگ به $trimmedHost ($targetIp)",
            "${steps.size} مرحله پورت"
        )

        var anyFatalFailure = false

        for (index in steps.indices) {
            val step = steps[index]
            initialStatuses[index] = initialStatuses[index].copy(state = StepRunState.SENDING)
            onStepUpdate(initialStatuses.toList())

            val stepStart = System.currentTimeMillis()
            val (ok, note) = when (step.protocol) {
                KnockProtocol.UDP -> sendUdpKnock(resolvedAddr, step.port)
                KnockProtocol.TCP -> sendTcpSynKnock(resolvedAddr, step.port)
            }
            val elapsed = (System.currentTimeMillis() - stepStart).coerceAtLeast(1L)

            initialStatuses[index] = initialStatuses[index].copy(
                state = if (ok) StepRunState.SENT else StepRunState.FAILED,
                latencyMs = elapsed,
                note = note
            )
            onStepUpdate(initialStatuses.toList())

            logger.log(
                LogCategory.KNOCK,
                "مرحله ${index + 1}/${steps.size}: ${step.protocol.label} پورت ${step.port}",
                "$note (${elapsed}ms)",
                isError = !ok
            )

            if (!ok) {
                anyFatalFailure = true
            }

            if (index < steps.lastIndex && step.delayMs > 0) {
                delay(step.delayMs.coerceIn(20, 3000).toLong())
            }
        }

        val totalMs = System.currentTimeMillis() - startAll
        logger.log(
            LogCategory.KNOCK,
            if (!anyFatalFailure) "توالی ناکینگ با موفقیت تکمیل شد" else "ناکینگ با خطای شبکه متوقف شد",
            "زمان کل: ${totalMs}ms | هدف: $targetIp",
            isError = anyFatalFailure
        )

        KnockSequenceResult(
            success = !anyFatalFailure,
            resolvedIp = targetIp,
            totalDurationMs = totalMs,
            stepStatuses = initialStatuses.toList()
        )
    }

    private fun sendUdpKnock(address: InetAddress, port: Int): Pair<Boolean, String> {
        return try {
            DatagramSocket().use { socket ->
                val payload = byteArrayOf(0x49, 0x47, 0x4B, 0x31) // "IGK1"
                val packet = DatagramPacket(payload, payload.size, address, port)
                socket.send(packet)
            }
            true to "UDP Datagram Sent"
        } catch (e: Exception) {
            false to (e.message ?: "UDP Send Failed")
        }
    }

    private fun sendTcpSynKnock(address: InetAddress, port: Int): Pair<Boolean, String> {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), TCP_KNOCK_TIMEOUT_MS)
            }
            true to "TCP SYN-ACK"
        } catch (_: SocketTimeoutException) {
            // Expected when MikroTik firewall drops SYN packets after recording src-address-list
            true to "TCP SYN Sent (Drop Timeout)"
        } catch (_: ConnectException) {
            // Expected when port responds with RST (ECONNREFUSED) after firewall rule logs it
            true to "TCP SYN Sent (RST/Refused)"
        } catch (e: Exception) {
            false to (e.message ?: "TCP Socket Error")
        }
    }

    suspend fun probeHost(host: String, port: Int, timeoutMs: Int = 900): ProbeResult =
        withContext(Dispatchers.IO) {
            val trimmed = host.trim()
            if (trimmed.isBlank()) {
                return@withContext ProbeResult(host, port, false, 0L, "آدرس خالی")
            }
            val start = System.currentTimeMillis()
            try {
                val addr = InetAddress.getByName(trimmed)
                // Try TCP connect first (fast LAN detection for SIP TCP/TLS or HTTP/SSH on Issabel)
                var tcpSuccess = false
                var tcpDetail = ""
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(addr, port), timeoutMs)
                    }
                    tcpSuccess = true
                    tcpDetail = "TCP/$port باز است"
                } catch (_: ConnectException) {
                    // Host IS reachable on LAN even if SIP is UDP-only (host responded with TCP RST!)
                    tcpSuccess = true
                    tcpDetail = "میزبان پاسخ داد (RST روی $port)"
                } catch (_: SocketTimeoutException) {
                    tcpDetail = "عدم پاسخ TCP در ${timeoutMs}ms"
                }

                if (tcpSuccess) {
                    val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                    return@withContext ProbeResult(trimmed, port, true, rtt, tcpDetail)
                }

                // Fallback ICMP/Echo reachability check
                val icmpOk = addr.isReachable(timeoutMs)
                val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                ProbeResult(
                    host = trimmed,
                    port = port,
                    reachable = icmpOk,
                    rttMs = rtt,
                    detail = if (icmpOk) "پینگ ICMP موفق (${rtt}ms)" else tcpDetail
                )
            } catch (e: Exception) {
                ProbeResult(
                    host = trimmed,
                    port = port,
                    reachable = false,
                    rttMs = 0L,
                    detail = e.message ?: "خطا در بررسی میزبان"
                )
            }
        }

    companion object {
        private const val TCP_KNOCK_TIMEOUT_MS = 350
    }
}
