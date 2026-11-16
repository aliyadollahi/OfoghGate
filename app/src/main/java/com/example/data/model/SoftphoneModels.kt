package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class NetworkMode(val titleFa: String, val subtitleFa: String) {
    AUTO_LAN_WAN(
        titleFa = "تشخیص خودکار (LAN / WAN)",
        subtitleFa = "تغییر هوشمند بین شبکه داخلی و خارجی با پورت ناکینگ"
    ),
    LAN_DIRECT(
        titleFa = "شبکه داخلی مستقیم (LAN)",
        subtitleFa = "اتصال مستقیم به IP و پورت داخلی ایزابل بدون ناکینگ"
    ),
    WAN_NAT_DIRECT(
        titleFa = "خارجی مستقیم با پورت NAT",
        subtitleFa = "اتصال مستقیم از بیرون با IP استاتیک یا DDNS بدون ناکینگ"
    ),
    WAN_PORT_KNOCK(
        titleFa = "خارجی + پورت ناکینگ میکروتیک",
        subtitleFa = "ارسال توالی بستههای ناک روی میکروتیک و سپس رجیستر SIP"
    )
}

@Serializable
enum class TransportType(val label: String) {
    UDP("UDP"),
    TCP("TCP"),
    TLS("TLS")
}

@Serializable
enum class KnockProtocol(val label: String) {
    UDP("UDP"),
    TCP("TCP")
}

@Serializable
data class KnockStep(
    val port: Int,
    val protocol: KnockProtocol = KnockProtocol.UDP,
    val delayMs: Int = 180
)

@Serializable
data class SoftphoneConfig(
    val extension: String = "",
    val authUser: String = "",
    val password: String = "",
    val displayName: String = "",
    val lanHost: String = "192.168.1.10",
    val lanPort: Int = 5060,
    val wanHost: String = "",
    val wanPort: Int = 5060,
    val officeWifiSsid: String = "",
    val transport: TransportType = TransportType.UDP,
    val networkMode: NetworkMode = NetworkMode.AUTO_LAN_WAN,
    val knockTargetHost: String = "",
    val knockSteps: List<KnockStep> = listOf(
        KnockStep(port = 11220, protocol = KnockProtocol.UDP, delayMs = 180),
        KnockStep(port = 22330, protocol = KnockProtocol.TCP, delayMs = 180),
        KnockStep(port = 33440, protocol = KnockProtocol.UDP, delayMs = 180)
    ),
    val knockIntervalMinutes: Int = 15,
    val preCallKnockGuardSeconds: Int = 600,
    val enableOpus: Boolean = true,
    val enablePcmu: Boolean = true,
    val enablePcma: Boolean = true,
    val adminPin: String = "1234",
    val serviceAutoStartOnBoot: Boolean = true
) {
    fun effectiveAuthUser(): String = authUser.ifBlank { extension }
    fun effectiveKnockHost(): String = knockTargetHost.ifBlank { wanHost }
    fun isConfigured(): Boolean = extension.isNotBlank() && password.isNotBlank() &&
        (lanHost.isNotBlank() || wanHost.isNotBlank())
}

enum class ConnectionPathState(val titleFa: String) {
    DISCONNECTED("قطع ارتباط"),
    DETECTING("در حال تشخیص شبکه..."),
    KNOCKING("در حال ارسال پورت ناک..."),
    REGISTERING("در حال رجیستر SIP..."),
    CONNECTED_LAN("متصل به LAN"),
    CONNECTED_WAN_NAT("متصل از WAN مستقیم"),
    CONNECTED_WAN_KNOCK("متصل از WAN با ناک"),
    ERROR("خطای اتصال")
}

enum class ActiveRoutePath(val labelFa: String) {
    NONE("نامشخص"),
    LAN("شبکه داخلی (LAN)"),
    WAN_NAT("خارجی مستقیم (NAT)"),
    WAN_KNOCK("خارجی + پورت ناکینگ")
}

enum class CallDirection(val labelFa: String) {
    INCOMING("ورودی"),
    OUTGOING("خروجی"),
    MISSED("بیپاسخ")
}

enum class ActiveCallState {
    IDLE,
    INCOMING_RINGING,
    OUTGOING_RINGING,
    CONNECTED,
    HELD,
    ENDED
}

data class CallUiState(
    val callId: String = "",
    val remoteNumber: String = "",
    val remoteName: String = "",
    val direction: CallDirection = CallDirection.OUTGOING,
    val state: ActiveCallState = ActiveCallState.IDLE,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isHeld: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isBluetoothOn: Boolean = false,
    val isBluetoothAvailable: Boolean = false,
    val codecName: String = "PCMU",
    val statusDetail: String = ""
)

enum class LogCategory(val labelFa: String) {
    KNOCK("ناکینگ"),
    SIP("SIP"),
    NETWORK("شبکه"),
    CALL("تماس"),
    SYSTEM("سیستم")
}

data class DiagnosticLogEntry(
    val id: Long,
    val timestampMs: Long,
    val category: LogCategory,
    val message: String,
    val detail: String = "",
    val isError: Boolean = false
)

data class KnockStepStatus(
    val stepIndex: Int,
    val step: KnockStep,
    val state: StepRunState,
    val latencyMs: Long = 0L,
    val note: String = ""
)

enum class StepRunState {
    IDLE,
    SENDING,
    SENT,
    FAILED
}

data class NetworkSnapshot(
    val isConnected: Boolean = false,
    val transportLabel: String = "بدون اینترنت",
    val localIp: String = "-",
    val wifiSsid: String = "",
    val activePath: ActiveRoutePath = ActiveRoutePath.NONE,
    val connectionState: ConnectionPathState = ConnectionPathState.DISCONNECTED,
    val sipStatusMessage: String = "آماده به کار",
    val sipResponseCode: Int = 0,
    val lastKnockEpochMs: Long = 0L,
    val nextKnockEpochMs: Long = 0L,
    val knockInProgress: Boolean = false,
    val stepStatuses: List<KnockStepStatus> = emptyList()
)
