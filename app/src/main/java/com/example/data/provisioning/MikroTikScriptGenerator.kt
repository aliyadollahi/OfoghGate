package com.example.data.provisioning

import com.example.data.model.KnockProtocol
import com.example.data.model.SoftphoneConfig
import com.example.data.model.TransportType

enum class RouterOsVersion(val label: String) {
    V7("RouterOS v7"),
    V6("RouterOS v6")
}

object MikroTikScriptGenerator {

    fun generate(config: SoftphoneConfig, rosVersion: RouterOsVersion): String {
        val steps = config.knockSteps.take(6)
        val lanIp = config.lanHost.ifBlank { "192.168.1.10" }
        val lanSipPort = config.lanPort.coerceIn(1, 65535)
        val wanSipPort = config.wanPort.coerceIn(1, 65535)
        val sipProto = if (config.transport == TransportType.UDP) "udp" else "tcp"
        // Add +5 minutes buffer beyond client knock interval so NAT/SIP session never drops
        val allowedMinutes = (config.knockIntervalMinutes + 5).coerceAtLeast(5)
        val stepWindowSeconds = 15

        val sb = StringBuilder()
        sb.appendLine("# =====================================================")
        sb.appendLine("# IssabelGate MikroTik Firewall & Port Knocking Script")
        sb.appendLine("# Target: ${rosVersion.label} | SIP: $sipProto/$wanSipPort -> $lanIp:$lanSipPort")
        sb.appendLine("# Knock Interval Sync: ${config.knockIntervalMinutes}m -> Timeout: ${allowedMinutes}m")
        sb.appendLine("# =====================================================")
        sb.appendLine()
        sb.appendLine("# 1. غیرفعالسازی SIP ALG (بسیار مهم برای عدم قطعی صدا و یکطرفه شدن مکالمه)")
        sb.appendLine("/ip firewall service-port set sip disabled=yes")
        sb.appendLine()
        sb.appendLine("# 2. قوانین مرحلهبندی Port Knocking")
        sb.appendLine("/ip firewall filter")

        steps.forEachIndexed { index, step ->
            val proto = if (step.protocol == KnockProtocol.UDP) "udp" else "tcp"
            val isLast = index == steps.lastIndex
            val targetList = if (isLast) "issabel_allowed" else "issabel_knock_stage_${index + 1}"
            val timeout = if (isLast) "00:${allowedMinutes.toString().padStart(2, '0')}:00" else "00:00:${stepWindowSeconds.toString().padStart(2, '0')}"
            val srcCondition = if (index == 0) "" else " src-address-list=issabel_knock_stage_${index}"
            val comment = "IssabelGate Knock Step ${index + 1} (${proto.uppercase()}/${step.port})"

            if (rosVersion == RouterOsVersion.V7) {
                sb.appendLine(
                    "add chain=input action=add-src-to-address-list address-list=$targetList " +
                        "address-list-timeout=$timeout protocol=$proto dst-port=${step.port}$srcCondition " +
                        "in-interface-list=WAN comment=\"$comment\""
                )
            } else {
                sb.appendLine(
                    "add chain=input action=add-src-to-address-list address-list=$targetList " +
                        "address-list-timeout=$timeout protocol=$proto dst-port=${step.port}$srcCondition " +
                        "comment=\"$comment\""
                )
            }
        }

        sb.appendLine()
        sb.appendLine("# 3. اجازه دسترسی به پورت SIP و بازه RTP فقط برای IPهای ناکشده")
        sb.appendLine(
            "add chain=forward action=accept protocol=$sipProto dst-port=$lanSipPort " +
                "src-address-list=issabel_allowed comment=\"Allow SIP from Knocked Clients\""
        )
        sb.appendLine(
            "add chain=forward action=accept protocol=udp dst-port=10000-20000 " +
                "src-address-list=issabel_allowed comment=\"Allow Issabel RTP Audio\""
        )
        sb.appendLine()
        sb.appendLine("# 4. قوانین DST-NAT به سمت سرور ایزابل ($lanIp)")
        sb.appendLine("/ip firewall nat")
        sb.appendLine(
            "add chain=dstnat action=dst-nat to-addresses=$lanIp to-ports=$lanSipPort " +
                "protocol=$sipProto dst-port=$wanSipPort src-address-list=issabel_allowed " +
                "comment=\"IssabelGate SIP NAT\""
        )
        sb.appendLine(
            "add chain=dstnat action=dst-nat to-addresses=$lanIp to-ports=10000-20000 " +
                "protocol=udp dst-port=10000-20000 src-address-list=issabel_allowed " +
                "comment=\"IssabelGate RTP Audio NAT\""
        )
        sb.appendLine()
        sb.appendLine("# 5. (اختیاری) بسته نگه داشتن پورت SIP از بیرون در جدول Input/Forward")
        sb.appendLine("/ip firewall filter")
        sb.appendLine(
            "add chain=forward action=drop protocol=$sipProto dst-port=$lanSipPort " +
                "src-address-list=!issabel_allowed comment=\"Drop Unauthorized SIP\""
        )

        return sb.toString()
    }
}
