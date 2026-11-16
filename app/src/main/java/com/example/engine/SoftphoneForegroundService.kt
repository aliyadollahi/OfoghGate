package com.example.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.IssabelGateApp
import com.example.MainActivity
import com.example.R
import com.example.data.model.ActiveRoutePath
import com.example.data.model.CallDirection
import com.example.data.model.ConnectionPathState
import com.example.data.model.LogCategory
import com.example.data.model.NetworkMode
import com.example.data.model.NetworkSnapshot
import com.example.data.model.SoftphoneConfig
import com.example.ui.call.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SoftphoneForegroundService : Service(), SipCallEventCallback {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var app: IssabelGateApp
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var reknockJob: Job? = null
    private var configObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        app = application as IssabelGateApp
        createNotificationChannels()
        SoftphoneConnectionService.registerPhoneAccount(this)
        acquireWifiLock()
        app.sipManager.initialize(this)

        startForegroundSafely(
            buildServiceNotification("در حال راهاندازی سرویس سافتفون...")
        )

        app.networkDetector.startMonitoring { env ->
            updateSnapshot {
                it.copy(
                    isConnected = env.isConnected,
                    transportLabel = env.transportLabel,
                    localIp = env.localIp,
                    wifiSsid = env.wifiSsid
                )
            }
            if (env.isConnected) {
                reknockJob?.cancel()
                reknockJob = serviceScope.launch {
                    delay(600)
                    evaluateAndConnect(reason = "تغییر شبکه / IP", forceKnock = true)
                }
            } else {
                updateSnapshot {
                    it.copy(
                        connectionState = ConnectionPathState.DISCONNECTED,
                        sipStatusMessage = "بدون دسترسی به اینترنت"
                    )
                }
            }
        }

        configObserverJob = serviceScope.launch {
            var firstEmit = true
            app.configStore.configFlow.collect { cfg ->
                if (firstEmit) {
                    firstEmit = false
                    if (cfg.isConfigured()) {
                        evaluateAndConnect(reason = "شروع اولیه سرویس", forceKnock = true)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECONFIGURE -> {
                serviceScope.launch {
                    evaluateAndConnect(reason = "اعمال تنظیمات جدید", forceKnock = true)
                }
            }

            ACTION_KNOCK_NOW -> {
                serviceScope.launch {
                    executeKnockAndRefresh(forceRegister = true)
                }
            }

            ACTION_SCHEDULED_KNOCK_FIRE -> {
                serviceScope.launch {
                    app.logger.log(LogCategory.KNOCK, "اجرای ناکینگ زمانبندیشده (Doze-Resistant)")
                    executeKnockAndRefresh(forceRegister = true)
                }
            }

            ACTION_OUTGOING_CALL -> {
                val number = intent.getStringExtra(EXTRA_TARGET_NUMBER).orEmpty()
                serviceScope.launch {
                    handleOutgoingCallWithGuard(number)
                }
            }

            ACTION_ANSWER_CALL -> {
                stopRingtoneAndVibrate()
                cancelIncomingCallNotification()
                app.sipManager.answerIncomingCall()
                launchCallActivity(isIncoming = false)
            }

            ACTION_DECLINE_CALL -> {
                stopRingtoneAndVibrate()
                cancelIncomingCallNotification()
                app.sipManager.declineIncomingCall()
                SoftphoneConnectionService.markDisconnected()
            }

            ACTION_HANGUP_CALL -> {
                stopRingtoneAndVibrate()
                cancelIncomingCallNotification()
                app.sipManager.hangupCurrentCall()
                SoftphoneConnectionService.markDisconnected()
            }

            ACTION_TOGGLE_HOLD -> {
                app.sipManager.toggleHold()
            }
        }
        return START_STICKY
    }

    private suspend fun evaluateAndConnect(reason: String, forceKnock: Boolean) {
        val config = app.configStore.loadConfig()
        val env = app.networkDetector.readCurrentEnvironment()

        updateSnapshot {
            it.copy(
                isConnected = env.isConnected,
                transportLabel = env.transportLabel,
                localIp = env.localIp,
                wifiSsid = env.wifiSsid
            )
        }

        if (!config.isConfigured()) {
            updateSnapshot {
                it.copy(
                    connectionState = ConnectionPathState.DISCONNECTED,
                    sipStatusMessage = "لطفاً اطلاعات داخلی را در تنظیمات وارد کنید"
                )
            }
            updateServiceNotification("نیاز به پیکربندی داخلی SIP")
            return
        }

        if (!env.isConnected) {
            updateSnapshot {
                it.copy(
                    connectionState = ConnectionPathState.DISCONNECTED,
                    sipStatusMessage = "شبکه قطع است"
                )
            }
            updateServiceNotification("شبکه قطع است")
            return
        }

        updateSnapshot { it.copy(connectionState = ConnectionPathState.DETECTING) }
        app.logger.log(LogCategory.NETWORK, "ارزیابی مسیر اتصال ($reason)", "حالت: ${config.networkMode.titleFa}")

        val chosenRoute = when (config.networkMode) {
            NetworkMode.LAN_DIRECT -> ActiveRoutePath.LAN
            NetworkMode.WAN_NAT_DIRECT -> ActiveRoutePath.WAN_NAT
            NetworkMode.WAN_PORT_KNOCK -> ActiveRoutePath.WAN_KNOCK
            NetworkMode.AUTO_LAN_WAN -> {
                if (app.networkDetector.isInsideOfficeLan(config)) {
                    ActiveRoutePath.LAN
                } else {
                    ActiveRoutePath.WAN_KNOCK
                }
            }
        }

        updateSnapshot { it.copy(activePath = chosenRoute) }

        when (chosenRoute) {
            ActiveRoutePath.LAN -> {
                app.knockScheduler.cancelScheduledKnock()
                updateSnapshot { it.copy(nextKnockEpochMs = 0L) }
                app.sipManager.registerAccount(
                    config = config,
                    host = config.lanHost,
                    port = config.lanPort,
                    routePath = ActiveRoutePath.LAN
                )
            }

            ActiveRoutePath.WAN_NAT -> {
                app.knockScheduler.cancelScheduledKnock()
                updateSnapshot { it.copy(nextKnockEpochMs = 0L) }
                app.sipManager.registerAccount(
                    config = config,
                    host = config.wanHost,
                    port = config.wanPort,
                    routePath = ActiveRoutePath.WAN_NAT
                )
            }

            ActiveRoutePath.WAN_KNOCK -> {
                if (forceKnock || shouldPerformGuardKnock(config)) {
                    executeKnockAndRefresh(forceRegister = true)
                } else {
                    app.sipManager.registerAccount(
                        config = config,
                        host = config.wanHost,
                        port = config.wanPort,
                        routePath = ActiveRoutePath.WAN_KNOCK
                    )
                }
            }

            ActiveRoutePath.NONE -> Unit
        }
    }

    private suspend fun executeKnockAndRefresh(forceRegister: Boolean) {
        val config = app.configStore.loadConfig()
        val targetHost = config.effectiveKnockHost()
        if (targetHost.isBlank()) {
            app.logger.log(LogCategory.KNOCK, "آدرس هدف پورت ناکینگ تنظیم نشده است", isError = true)
            return
        }

        acquireTemporaryWakeLock(8_000L)
        updateSnapshot {
            it.copy(
                knockInProgress = true,
                connectionState = ConnectionPathState.KNOCKING,
                sipStatusMessage = "در حال ارسال بستههای ناک..."
            )
        }

        val result = app.portKnocker.executeKnockSequence(
            targetHost = targetHost,
            steps = config.knockSteps
        ) { statuses ->
            updateSnapshot { it.copy(stepStatuses = statuses) }
        }

        val now = System.currentTimeMillis()
        if (result.success) {
            app.configStore.setLastKnockEpochMs(now)
        }
        val nextEpoch = if (config.networkMode == NetworkMode.WAN_PORT_KNOCK ||
            snapshot.value.activePath == ActiveRoutePath.WAN_KNOCK
        ) {
            app.knockScheduler.scheduleNextKnock(config.knockIntervalMinutes).also {
                app.configStore.setNextKnockEpochMs(it)
            }
        } else {
            0L
        }

        updateSnapshot {
            it.copy(
                knockInProgress = false,
                lastKnockEpochMs = if (result.success) now else it.lastKnockEpochMs,
                nextKnockEpochMs = nextEpoch,
                stepStatuses = result.stepStatuses
            )
        }

        if (forceRegister && config.isConfigured() && config.wanHost.isNotBlank()) {
            delay(250)
            app.sipManager.registerAccount(
                config = config,
                host = config.wanHost,
                port = config.wanPort,
                routePath = ActiveRoutePath.WAN_KNOCK
            )
        }
    }

    private suspend fun handleOutgoingCallWithGuard(number: String) {
        val config = app.configStore.loadConfig()
        val route = snapshot.value.activePath
        if (route == ActiveRoutePath.WAN_KNOCK && shouldPerformGuardKnock(config)) {
            app.logger.log(
                LogCategory.KNOCK,
                "محافظ پیش از تماس (Pre-Call Knock Guard) فعال شد",
                "ارسال ناک فوری قبل از INVITE به $number"
            )
            executeKnockAndRefresh(forceRegister = false)
            app.sipManager.refreshRegistration()
            delay(200)
        }
        app.sipManager.makeCall(number)
        launchCallActivity(isIncoming = false)
    }

    private fun shouldPerformGuardKnock(config: SoftphoneConfig): Boolean {
        val last = app.configStore.getLastKnockEpochMs()
        if (last <= 0L) return true
        val elapsedSec = (System.currentTimeMillis() - last) / 1000L
        return elapsedSec >= config.preCallKnockGuardSeconds
    }

    override fun onIncomingCallReceived(number: String, displayName: String) {
        acquireTemporaryWakeLock(45_000L)
        SoftphoneConnectionService.notifyIncomingCall(this, number, displayName)
        startRingtoneAndVibrate()
        postIncomingCallNotification(number, displayName)
        launchCallActivity(isIncoming = true)
    }

    override fun onCallConnected(number: String, displayName: String, direction: CallDirection) {
        stopRingtoneAndVibrate()
        cancelIncomingCallNotification()
        SoftphoneConnectionService.markActive()
    }

    override fun onCallEnded(
        number: String,
        displayName: String,
        direction: CallDirection,
        durationSeconds: Long,
        wasMissed: Boolean
    ) {
        stopRingtoneAndVibrate()
        cancelIncomingCallNotification()
        SoftphoneConnectionService.markDisconnected()
        serviceScope.launch {
            val recordedDir = if (wasMissed) CallDirection.MISSED else direction
            app.callRepository.logCall(
                number = number,
                displayName = displayName,
                direction = recordedDir,
                durationSeconds = durationSeconds,
                networkModeUsed = snapshot.value.activePath.labelFa
            )
        }
    }

    override fun onRegistrationUpdated(state: ConnectionPathState, message: String) {
        updateSnapshot {
            it.copy(
                connectionState = state,
                sipStatusMessage = message
            )
        }
        val cfg = app.configStore.loadConfig()
        val title = if (cfg.extension.isNotBlank()) {
            "داخلی ${cfg.extension} | ${state.titleFa}"
        } else {
            state.titleFa
        }
        updateServiceNotification(title)
    }

    private fun launchCallActivity(isIncoming: Boolean) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra(IncomingCallActivity.EXTRA_IS_INCOMING, isIncoming)
        }
        runCatching { startActivity(intent) }
    }

    private fun postIncomingCallNotification(number: String, displayName: String) {
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(IncomingCallActivity.EXTRA_IS_INCOMING, true)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this,
            5100,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerPi = PendingIntent.getBroadcast(
            this,
            5101,
            Intent(this, BootAndAlarmReceiver::class.java).apply {
                action = BootAndAlarmReceiver.ACTION_CALL_ANSWER
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declinePi = PendingIntent.getBroadcast(
            this,
            5102,
            Intent(this, BootAndAlarmReceiver::class.java).apply {
                action = BootAndAlarmReceiver.ACTION_CALL_DECLINE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = Person.Builder()
                .setName(displayName.ifBlank { number })
                .setImportant(true)
                .build()
            Notification.Builder(this, CHANNEL_CALLS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("تماس ورودی ایزابل")
                .setContentText("داخلی: $number")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPi, true)
                .setStyle(Notification.CallStyle.forIncomingCall(caller, declinePi, answerPi))
                .build()
        } else {
            NotificationCompat.Builder(this, CHANNEL_CALLS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("تماس ورودی: $displayName")
                .setContentText("شماره داخلی: $number")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenPi, true)
                .addAction(android.R.drawable.ic_menu_call, "پاسخ", answerPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "رد تماس", declinePi)
                .build()
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_INCOMING_CALL, notification)
    }

    private fun cancelIncomingCallNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_INCOMING_CALL)
    }

    private fun startRingtoneAndVibrate() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        }
        runCatching {
            val pattern = longArrayOf(0, 700, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
        }
    }

    private fun stopRingtoneAndVibrate() {
        runCatching {
            ringtone?.stop()
            ringtone = null
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.cancel()
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.cancel()
            }
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // IMPORTANCE_HIGH keeps the ForegroundService and Linphone SIP registration prioritized even in Doze mode
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "حفظ اتصال دائمی SIP و پورت ناکینگ ایزابل در پسزمینه و حالت Doze"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            getString(R.string.notification_channel_calls),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        nm.createNotificationChannel(serviceChannel)
        nm.createNotificationChannel(callsChannel)
    }

    private fun buildServiceNotification(statusText: String): Notification {
        val openPi = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openPi)
            .build()
    }

    private fun updateServiceNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_SERVICE, buildServiceNotification(statusText))
    }

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID_SERVICE, notification)
        }
    }

    private fun acquireWifiLock() {
        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            if (wifiLock == null) {
                wifiLock = wm.createWifiLock(mode, "IssabelGate::SipWifiLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        }
    }

    private fun acquireTemporaryWakeLock(timeoutMs: Long) {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IssabelGate::KnockSipLock")
            }
            wakeLock?.acquire(timeoutMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRingtoneAndVibrate()
        app.networkDetector.stopMonitoring()
        configObserverJob?.cancel()
        reknockJob?.cancel()
        serviceScope.cancel()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_SERVICE = "issabel_gate_service_high_channel"
        const val CHANNEL_CALLS = "issabel_gate_calls_channel"
        private const val NOTIF_ID_SERVICE = 1001
        private const val NOTIF_ID_INCOMING_CALL = 1002

        const val ACTION_RECONFIGURE = "com.example.ACTION_RECONFIGURE"
        const val ACTION_KNOCK_NOW = "com.example.ACTION_KNOCK_NOW"
        const val ACTION_SCHEDULED_KNOCK_FIRE = "com.example.ACTION_SCHEDULED_KNOCK_FIRE"
        const val ACTION_OUTGOING_CALL = "com.example.ACTION_OUTGOING_CALL"
        const val ACTION_ANSWER_CALL = "com.example.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.ACTION_DECLINE_CALL"
        const val ACTION_HANGUP_CALL = "com.example.ACTION_HANGUP_CALL"
        const val ACTION_TOGGLE_HOLD = "com.example.ACTION_TOGGLE_HOLD"
        const val EXTRA_TARGET_NUMBER = "extra_target_number"

        private val _snapshot = MutableStateFlow(NetworkSnapshot())
        val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

        private fun updateSnapshot(transform: (NetworkSnapshot) -> NetworkSnapshot) {
            _snapshot.value = transform(_snapshot.value)
        }

        fun startOrUpdate(context: Context) {
            val intent = Intent(context, SoftphoneForegroundService::class.java).apply {
                action = ACTION_RECONFIGURE
            }
            context.startForegroundService(intent)
        }

        fun sendAction(context: Context, action: String, targetNumber: String? = null) {
            val intent = Intent(context, SoftphoneForegroundService::class.java).apply {
                this.action = action
                if (targetNumber != null) putExtra(EXTRA_TARGET_NUMBER, targetNumber)
            }
            context.startForegroundService(intent)
        }
    }
}
