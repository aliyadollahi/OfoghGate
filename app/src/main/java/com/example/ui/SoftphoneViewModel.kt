package com.example.ui

import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IssabelGateApp
import com.example.data.local.CallRecordEntity
import com.example.data.local.QuickExtensionEntity
import com.example.data.model.CallUiState
import com.example.data.model.DiagnosticLogEntry
import com.example.data.model.LogCategory
import com.example.data.model.NetworkMode
import com.example.data.model.NetworkSnapshot
import com.example.data.model.SoftphoneConfig
import com.example.data.provisioning.MikroTikScriptGenerator
import com.example.data.provisioning.ProvisionDecodeResult
import com.example.data.provisioning.ProvisioningCodec
import com.example.data.provisioning.RouterOsVersion
import com.example.engine.ProbeResult
import com.example.engine.SoftphoneForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProbeSummary(
    val isRunning: Boolean = false,
    val lanResult: ProbeResult? = null,
    val wanResult: ProbeResult? = null
)

class SoftphoneViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as IssabelGateApp
    private var toneGenerator: ToneGenerator? = null

    val config: StateFlow<SoftphoneConfig> = app.configStore.configFlow
    val networkSnapshot: StateFlow<NetworkSnapshot> = SoftphoneForegroundService.snapshot
    val callUiState: StateFlow<CallUiState> = app.sipManager.callUiState
    val logs: StateFlow<List<DiagnosticLogEntry>> = app.logger.logs

    val recentCalls: StateFlow<List<CallRecordEntity>> = app.callRepository.recentCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickExtensions: StateFlow<List<QuickExtensionEntity>> = app.callRepository.quickExtensions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dialDigits = MutableStateFlow("")
    val dialDigits: StateFlow<String> = _dialDigits.asStateFlow()

    private val _probeSummary = MutableStateFlow(ProbeSummary())
    val probeSummary: StateFlow<ProbeSummary> = _probeSummary.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        runCatching {
            toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 65)
        }
    }

    fun startServiceIfConfigured() {
        SoftphoneForegroundService.startOrUpdate(app)
    }

    fun appendDialDigit(digit: String) {
        _dialDigits.value = (_dialDigits.value + digit).take(24)
        playDtmfTone(digit)
    }

    fun setDialNumber(number: String) {
        _dialDigits.value = number.take(24)
    }

    fun backspaceDialDigit() {
        val current = _dialDigits.value
        if (current.isNotEmpty()) {
            _dialDigits.value = current.dropLast(1)
        }
    }

    fun clearDialDigits() {
        _dialDigits.value = ""
    }

    fun placeCall(overrideNumber: String? = null) {
        val target = (overrideNumber ?: _dialDigits.value).trim()
        if (target.isBlank()) {
            _statusBanner.value = "لطفاً شماره داخلی یا مقصد را وارد کنید"
            return
        }
        SoftphoneForegroundService.sendAction(
            app,
            SoftphoneForegroundService.ACTION_OUTGOING_CALL,
            targetNumber = target
        )
    }

    fun saveConfig(newConfig: SoftphoneConfig) {
        app.configStore.saveConfig(newConfig)
        app.logger.log(
            LogCategory.SYSTEM,
            "تنظیمات جدید ذخیره شد",
            "داخلی: ${newConfig.extension} | حالت: ${newConfig.networkMode.titleFa}"
        )
        SoftphoneForegroundService.startOrUpdate(app)
        _statusBanner.value = "تنظیمات ذخیره و سرویس بروزرسانی شد"
    }

    fun switchNetworkMode(mode: NetworkMode) {
        val updated = config.value.copy(networkMode = mode)
        saveConfig(updated)
    }

    fun triggerImmediateKnock() {
        SoftphoneForegroundService.sendAction(app, SoftphoneForegroundService.ACTION_KNOCK_NOW)
    }

    fun runHostProbes() {
        val cfg = config.value
        viewModelScope.launch {
            _probeSummary.value = ProbeSummary(isRunning = true)
            val lanRes = if (cfg.lanHost.isNotBlank()) {
                app.portKnocker.probeHost(cfg.lanHost, cfg.lanPort, 1200)
            } else null
            val wanRes = if (cfg.wanHost.isNotBlank()) {
                app.portKnocker.probeHost(cfg.wanHost, cfg.wanPort, 1400)
            } else null
            _probeSummary.value = ProbeSummary(
                isRunning = false,
                lanResult = lanRes,
                wanResult = wanRes
            )
            lanRes?.let {
                app.logger.log(
                    LogCategory.NETWORK,
                    "تست پینگ/دسترسی LAN (${it.host}:${it.port})",
                    it.detail,
                    isError = !it.reachable
                )
            }
            wanRes?.let {
                app.logger.log(
                    LogCategory.NETWORK,
                    "تست دسترسی WAN (${it.host}:${it.port})",
                    it.detail,
                    isError = !it.reachable
                )
            }
        }
    }

    fun decodeProvisionPayload(raw: String, pin: String?): ProvisionDecodeResult {
        val result = ProvisioningCodec.decode(raw, pin)
        if (result is ProvisionDecodeResult.Success) {
            saveConfig(result.config)
            _statusBanner.value = "پیکربندی با موفقیت از QR/رشته دستور بارگذاری شد"
        }
        return result
    }

    fun encodeProvisionPayload(cfg: SoftphoneConfig, pin: String?): String {
        return if (pin.isNullOrBlank()) {
            ProvisioningCodec.encodePlain(cfg)
        } else {
            ProvisioningCodec.encodeEncrypted(cfg, pin)
        }
    }

    fun buildMikroTikScript(cfg: SoftphoneConfig, rosVersion: RouterOsVersion): String {
        return MikroTikScriptGenerator.generate(cfg, rosVersion)
    }

    fun addQuickExtension(extension: String, label: String, department: String) {
        viewModelScope.launch {
            app.callRepository.saveQuickExtension(extension, label, department)
        }
    }

    fun deleteQuickExtension(id: Long) {
        viewModelScope.launch {
            app.callRepository.deleteQuickExtension(id)
        }
    }

    fun clearCallHistory() {
        viewModelScope.launch {
            app.callRepository.clearCalls()
        }
    }

    fun clearDiagnosticLogs() {
        app.logger.clear()
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = app.getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false
    }

    fun buildBatteryOptimizationIntent(): Intent {
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${app.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun canScheduleExactAlarms(): Boolean = app.knockScheduler.canScheduleExactAlarms()

    fun buildExactAlarmSettingsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${app.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else null
    }

    fun buildFullScreenIntentSettings(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${app.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else null
    }

    private fun playDtmfTone(digit: String) {
        val toneType = when (digit) {
            "0" -> ToneGenerator.TONE_DTMF_0
            "1" -> ToneGenerator.TONE_DTMF_1
            "2" -> ToneGenerator.TONE_DTMF_2
            "3" -> ToneGenerator.TONE_DTMF_3
            "4" -> ToneGenerator.TONE_DTMF_4
            "5" -> ToneGenerator.TONE_DTMF_5
            "6" -> ToneGenerator.TONE_DTMF_6
            "7" -> ToneGenerator.TONE_DTMF_7
            "8" -> ToneGenerator.TONE_DTMF_8
            "9" -> ToneGenerator.TONE_DTMF_9
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        runCatching { toneGenerator?.startTone(toneType, 110) }
    }

    override fun onCleared() {
        runCatching { toneGenerator?.release() }
        super.onCleared()
    }
}
