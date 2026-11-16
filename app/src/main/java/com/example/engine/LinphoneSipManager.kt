package com.example.engine

import android.content.Context
import android.media.AudioManager
import com.example.data.model.ActiveCallState
import com.example.data.model.ActiveRoutePath
import com.example.data.model.CallDirection
import com.example.data.model.CallUiState
import com.example.data.model.ConnectionPathState
import com.example.data.model.LogCategory
import com.example.data.model.SoftphoneConfig
import com.example.data.model.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType as LinphoneTransport

interface SipCallEventCallback {
    fun onIncomingCallReceived(number: String, displayName: String)
    fun onCallConnected(number: String, displayName: String, direction: CallDirection)
    fun onCallEnded(
        number: String,
        displayName: String,
        direction: CallDirection,
        durationSeconds: Long,
        wasMissed: Boolean
    )
    fun onRegistrationUpdated(state: ConnectionPathState, message: String)
}

class LinphoneSipManager(
    private val context: Context,
    private val logger: DiagnosticLogger
) {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var core: Core? = null
    private var activeRoutePath: ActiveRoutePath = ActiveRoutePath.NONE
    private var currentConfig: SoftphoneConfig = SoftphoneConfig()
    private var eventCallback: SipCallEventCallback? = null
    private var timerJob: Job? = null
    private var currentCallWasConnected = false

    private val _callUiState = MutableStateFlow(CallUiState())
    val callUiState: StateFlow<CallUiState> = _callUiState.asStateFlow()

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            val detail = message.ifBlank { state.name }
            when (state) {
                RegistrationState.Ok -> {
                    _isRegistered.value = true
                    val connectedState = when (activeRoutePath) {
                        ActiveRoutePath.LAN -> ConnectionPathState.CONNECTED_LAN
                        ActiveRoutePath.WAN_NAT -> ConnectionPathState.CONNECTED_WAN_NAT
                        ActiveRoutePath.WAN_KNOCK -> ConnectionPathState.CONNECTED_WAN_KNOCK
                        ActiveRoutePath.NONE -> ConnectionPathState.CONNECTED_LAN
                    }
                    logger.log(LogCategory.SIP, "رجیستر SIP موفق (200 OK)", "${activeRoutePath.labelFa} | $detail")
                    eventCallback?.onRegistrationUpdated(connectedState, "رجیستر موفق ($detail)")
                }

                RegistrationState.Progress, RegistrationState.Refreshing -> {
                    logger.log(LogCategory.SIP, "در حال مذاکره رجیستر SIP...", detail)
                    eventCallback?.onRegistrationUpdated(
                        ConnectionPathState.REGISTERING,
                        "در حال رجیستر..."
                    )
                }

                RegistrationState.Cleared -> {
                    _isRegistered.value = false
                    logger.log(LogCategory.SIP, "رجیستر SIP پاک شد", detail)
                    eventCallback?.onRegistrationUpdated(ConnectionPathState.DISCONNECTED, "رجیستر متوقف شد")
                }

                RegistrationState.Failed -> {
                    _isRegistered.value = false
                    logger.log(LogCategory.SIP, "خطای رجیستر SIP", detail, isError = true)
                    eventCallback?.onRegistrationUpdated(
                        ConnectionPathState.ERROR,
                        "خطای رجیستر: $detail"
                    )
                }

                else -> Unit
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            val remoteNum: String = call.remoteAddress?.username?.takeIf { it.isNotBlank() }
                ?: call.remoteAddressAsString.orEmpty()
            val remoteName: String = call.remoteAddress?.displayName?.takeIf { it.isNotBlank() }
                ?: remoteNum
            val dir = if (call.dir == Call.Dir.Incoming) CallDirection.INCOMING else CallDirection.OUTGOING
            val codec = call.currentParams?.usedAudioPayloadType?.mimeType ?: "PCMU"

            logger.log(
                LogCategory.CALL,
                "وضعیت تماس [${state.name}]: $remoteNum",
                message.ifBlank { codec },
                isError = state == Call.State.Error
            )

            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    currentCallWasConnected = false
                    _callUiState.value = CallUiState(
                        callId = call.callLog?.callId ?: remoteNum,
                        remoteNumber = remoteNum,
                        remoteName = remoteName,
                        direction = CallDirection.INCOMING,
                        state = ActiveCallState.INCOMING_RINGING,
                        isBluetoothAvailable = hasBluetoothDevice(),
                        codecName = codec,
                        statusDetail = "تماس ورودی از ایزابل..."
                    )
                    if (state == Call.State.IncomingReceived) {
                        eventCallback?.onIncomingCallReceived(remoteNum, remoteName)
                    }
                }

                Call.State.OutgoingInit, Call.State.OutgoingProgress,
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> {
                    currentCallWasConnected = false
                    configureAudioForCall()
                    _callUiState.value = CallUiState(
                        callId = call.callLog?.callId ?: remoteNum,
                        remoteNumber = remoteNum,
                        remoteName = remoteName,
                        direction = CallDirection.OUTGOING,
                        state = ActiveCallState.OUTGOING_RINGING,
                        isBluetoothAvailable = hasBluetoothDevice(),
                        codecName = codec,
                        statusDetail = if (state == Call.State.OutgoingRinging) "در حال زنگ خوردن..." else "در حال برقراری..."
                    )
                }

                Call.State.Connected, Call.State.StreamsRunning -> {
                    currentCallWasConnected = true
                    configureAudioForCall()
                    startCallDurationTicker(call)
                    _callUiState.value = _callUiState.value.copy(
                        remoteNumber = remoteNum,
                        remoteName = remoteName,
                        direction = dir,
                        state = ActiveCallState.CONNECTED,
                        isHeld = false,
                        isMuted = !core.isMicEnabled,
                        isBluetoothAvailable = hasBluetoothDevice(),
                        codecName = codec,
                        statusDetail = "مکالمه برقرار ($codec)"
                    )
                    eventCallback?.onCallConnected(remoteNum, remoteName, dir)
                }

                Call.State.Paused, Call.State.Pausing, Call.State.PausedByRemote -> {
                    _callUiState.value = _callUiState.value.copy(
                        state = ActiveCallState.HELD,
                        isHeld = true,
                        statusDetail = "تماس در انتظار (Hold)"
                    )
                }

                Call.State.End, Call.State.Error, Call.State.Released -> {
                    if (state == Call.State.Released && _callUiState.value.state == ActiveCallState.IDLE) {
                        return
                    }
                    stopCallDurationTicker()
                    restoreAudioAfterCall()
                    val finalDuration = call.duration.toLong().coerceAtLeast(_callUiState.value.durationSeconds)
                    val wasMissed = dir == CallDirection.INCOMING && !currentCallWasConnected
                    _callUiState.value = _callUiState.value.copy(
                        state = ActiveCallState.ENDED,
                        statusDetail = if (state == Call.State.Error) "خطا: $message" else "تماس پایان یافت"
                    )
                    eventCallback?.onCallEnded(remoteNum, remoteName, dir, finalDuration, wasMissed)
                    mainScope.launch {
                        delay(700)
                        if (core.callsNb == 0) {
                            _callUiState.value = CallUiState()
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    fun initialize(callback: SipCallEventCallback) {
        eventCallback = callback
        mainScope.launch {
            ensureCoreStarted()
        }
    }

    private fun ensureCoreStarted(): Core? {
        core?.let { return it }
        return runCatching {
            val factory = Factory.instance()
            factory.enableLogCollection(org.linphone.core.LogCollectionState.Disabled)
            val linphoneConfig = factory.createConfig(null).apply {
                setBool("video", "capture", false)
                setBool("video", "display", false)
                setBool("video", "auto_initiate", false)
                setBool("video", "auto_accept", false)
            }
            val created = factory.createCoreWithConfig(linphoneConfig, context.applicationContext)
            created.isVideoCaptureEnabled = false
            created.isVideoDisplayEnabled = false
            created.isVideoPreviewEnabled = false
            created.setUserAgent("IssabelGate-Android", "1.0")
            created.isAutoIterateEnabled = true
            created.autoIterateForegroundSchedule = 20
            created.autoIterateBackgroundSchedule = 20
            created.setKeepAliveEnabled(true)
            created.isVibrationOnIncomingCallEnabled = false
            created.addListener(coreListener)
            created.start()
            core = created
            logger.log(LogCategory.SIP, "هسته Linphone SDK 5.3 راه‌اندازی شد")
            created
        }.onFailure {
            logger.log(LogCategory.SIP, "خطا در راه‌اندازی هسته Linphone", it.message ?: "", isError = true)
        }.getOrNull()
    }

    fun registerAccount(config: SoftphoneConfig, host: String, port: Int, routePath: ActiveRoutePath) {
        currentConfig = config
        activeRoutePath = routePath
        mainScope.launch {
            val c = ensureCoreStarted() ?: return@launch
            if (config.extension.isBlank() || config.password.isBlank() || host.isBlank()) {
                logger.log(LogCategory.SIP, "اطلاعات حساب داخلی کامل نیست", isError = true)
                return@launch
            }

            applyAudioCodecs(c, config)

            c.clearAccounts()
            c.clearAllAuthInfo()

            val factory = Factory.instance()
            val authInfo = factory.createAuthInfo(
                config.effectiveAuthUser(),
                null,
                config.password,
                null,
                null,
                host
            )
            c.addAuthInfo(authInfo)

            val identityStr = "sip:${config.extension}@$host"
            val identity = factory.createAddress(identityStr)
            if (identity != null && config.displayName.isNotBlank()) {
                identity.displayName = config.displayName
            }

            val transportStr = when (config.transport) {
                TransportType.UDP -> "udp"
                TransportType.TCP -> "tcp"
                TransportType.TLS -> "tls"
            }
            val linphoneTransport = when (config.transport) {
                TransportType.UDP -> LinphoneTransport.Udp
                TransportType.TCP -> LinphoneTransport.Tcp
                TransportType.TLS -> LinphoneTransport.Tls
            }

            val serverAddress = factory.createAddress("sip:$host:$port;transport=$transportStr")
            val params = c.createAccountParams().apply {
                if (identity != null) setIdentityAddress(identity)
                if (serverAddress != null) setServerAddress(serverAddress)
                setTransport(linphoneTransport)
                setRegisterEnabled(true)
                setExpires(120)
            }

            val account = c.createAccount(params)
            c.addAccount(account)
            c.defaultAccount = account
            c.setKeepAliveEnabled(true)

            logger.log(
                LogCategory.SIP,
                "ارسال درخواست REGISTER برای داخلی ${config.extension}",
                "سرور: $host:$port (${config.transport.label}) | مسیر: ${routePath.labelFa}"
            )
        }
    }

    fun refreshRegistration() {
        mainScope.launch {
            core?.refreshRegisters()
            logger.log(LogCategory.SIP, "تمدید رجیستر SIP ارسال شد")
        }
    }

    fun unregister() {
        mainScope.launch {
            core?.let { c ->
                c.clearAccounts()
                c.clearAllAuthInfo()
            }
            _isRegistered.value = false
        }
    }

    fun makeCall(targetNumber: String) {
        val cleanNumber = targetNumber.trim()
        if (cleanNumber.isBlank()) return
        mainScope.launch {
            val c = ensureCoreStarted() ?: return@launch
            val host = c.defaultAccount?.params?.serverAddress?.domain
                ?: if (activeRoutePath == ActiveRoutePath.LAN) currentConfig.lanHost else currentConfig.wanHost
            val port = c.defaultAccount?.params?.serverAddress?.port
                ?: if (activeRoutePath == ActiveRoutePath.LAN) currentConfig.lanPort else currentConfig.wanPort

            val sipUri = if (cleanNumber.startsWith("sip:")) {
                cleanNumber
            } else {
                "sip:$cleanNumber@$host:$port"
            }
            val address = c.createAddress(sipUri)
            if (address == null) {
                logger.log(LogCategory.CALL, "شماره یا آدرس SIP نامعتبر است: $sipUri", isError = true)
                return@launch
            }
            val params = c.createCallParams(null)?.apply {
                isAudioEnabled = true
                isVideoEnabled = false
            }
            if (params != null) {
                c.inviteAddressWithParams(address, params)
            } else {
                c.inviteAddress(address)
            }
            logger.log(LogCategory.CALL, "برقراری تماس خروجی به $cleanNumber", sipUri)
        }
    }

    fun answerIncomingCall() {
        mainScope.launch {
            val call = core?.currentCall ?: core?.calls?.firstOrNull()
            call?.accept()
            logger.log(LogCategory.CALL, "پاسخ به تماس ورودی")
        }
    }

    fun declineIncomingCall() {
        mainScope.launch {
            val call = core?.currentCall ?: core?.calls?.firstOrNull()
            call?.decline(Reason.Declined)
            logger.log(LogCategory.CALL, "رد تماس ورودی")
        }
    }

    fun hangupCurrentCall() {
        mainScope.launch {
            val call = core?.currentCall ?: core?.calls?.firstOrNull()
            call?.terminate()
            logger.log(LogCategory.CALL, "قطع تماس فعال")
        }
    }

    fun toggleMute() {
        mainScope.launch {
            val c = core ?: return@launch
            val nextMute = c.isMicEnabled
            c.isMicEnabled = !nextMute
            _callUiState.value = _callUiState.value.copy(isMuted = nextMute)
        }
    }

    fun toggleHold() {
        mainScope.launch {
            val call = core?.currentCall ?: core?.calls?.firstOrNull() ?: return@launch
            if (call.state == Call.State.Paused || call.state == Call.State.PausedByRemote) {
                call.resume()
                _callUiState.value = _callUiState.value.copy(isHeld = false)
            } else {
                call.pause()
                _callUiState.value = _callUiState.value.copy(isHeld = true)
            }
        }
    }

    fun toggleSpeaker() {
        mainScope.launch {
            val c = core ?: return@launch
            val call = c.currentCall ?: c.calls.firstOrNull()
            val nextSpeaker = !_callUiState.value.isSpeakerOn
            val targetType = if (nextSpeaker) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
            val device = c.audioDevices.firstOrNull { it.type == targetType }
            if (device != null && call != null) {
                call.outputAudioDevice = device
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = nextSpeaker
            }
            _callUiState.value = _callUiState.value.copy(
                isSpeakerOn = nextSpeaker,
                isBluetoothOn = false
            )
        }
    }

    fun toggleBluetooth() {
        mainScope.launch {
            val c = core ?: return@launch
            val call = c.currentCall ?: c.calls.firstOrNull() ?: return@launch
            val nextBt = !_callUiState.value.isBluetoothOn
            val targetDevice = if (nextBt) {
                c.audioDevices.firstOrNull {
                    it.type == AudioDevice.Type.Bluetooth || it.type == AudioDevice.Type.BluetoothA2DP
                }
            } else {
                c.audioDevices.firstOrNull { it.type == AudioDevice.Type.Earpiece }
            }
            if (targetDevice != null) {
                call.outputAudioDevice = targetDevice
                _callUiState.value = _callUiState.value.copy(
                    isBluetoothOn = nextBt,
                    isSpeakerOn = false
                )
            }
        }
    }

    fun sendDtmf(digit: Char) {
        mainScope.launch {
            val call = core?.currentCall ?: core?.calls?.firstOrNull() ?: return@launch
            call.sendDtmf(digit)
            logger.log(LogCategory.CALL, "ارسال DTMF: $digit")
        }
    }

    private fun applyAudioCodecs(core: Core, config: SoftphoneConfig) {
        for (pt in core.audioPayloadTypes) {
            val mime = pt.mimeType.lowercase()
            when {
                mime.contains("opus") -> pt.enable(config.enableOpus)
                mime.contains("pcmu") -> pt.enable(config.enablePcmu)
                mime.contains("pcma") -> pt.enable(config.enablePcma)
            }
        }
    }

    private fun hasBluetoothDevice(): Boolean {
        val devices = core?.audioDevices ?: return false
        return devices.any {
            it.type == AudioDevice.Type.Bluetooth || it.type == AudioDevice.Type.BluetoothA2DP
        }
    }

    private fun configureAudioForCall() {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
    }

    private fun restoreAudioAfterCall() {
        runCatching {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun startCallDurationTicker(call: Call) {
        if (timerJob?.isActive == true) return
        timerJob = mainScope.launch {
            while (true) {
                delay(1000L)
                val sec = call.duration.toLong()
                if (_callUiState.value.state == ActiveCallState.CONNECTED ||
                    _callUiState.value.state == ActiveCallState.HELD
                ) {
                    _callUiState.value = _callUiState.value.copy(durationSeconds = sec)
                }
            }
        }
    }

    private fun stopCallDurationTicker() {
        timerJob?.cancel()
        timerJob = null
    }

    fun shutdown() {
        stopCallDurationTicker()
        mainScope.launch {
            core?.let {
                it.removeListener(coreListener)
                it.stop()
            }
            core = null
        }
    }
}
