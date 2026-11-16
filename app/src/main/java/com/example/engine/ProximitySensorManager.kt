package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.example.data.model.LogCategory

/**
 * Manages the device proximity sensor and PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
 * so the screen turns off automatically when the user holds the phone to their ear
 * during an active VoIP call, while keeping the screen on when speakerphone is active.
 */
class ProximitySensorManager(
    context: Context,
    private val logger: DiagnosticLogger? = null
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var isCallActive = false
    private var isSpeakerOn = false
    private var isNearEar = false

    fun startForActiveCall(speakerOn: Boolean = false) {
        isCallActive = true
        isSpeakerOn = speakerOn
        registerSensorListener()
        updateWakeLockState()
    }

    fun updateSpeakerState(speakerOn: Boolean) {
        isSpeakerOn = speakerOn
        updateWakeLockState()
    }

    fun stop() {
        isCallActive = false
        isSpeakerOn = false
        isNearEar = false
        unregisterSensorListener()
        releaseWakeLock()
    }

    fun isWakeLockHeld(): Boolean = proximityWakeLock?.isHeld == true

    private fun updateWakeLockState() {
        if (isCallActive && !isSpeakerOn) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = powerManager ?: return
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                return
            }
            if (proximityWakeLock == null) {
                proximityWakeLock = pm.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    WAKELOCK_TAG
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (proximityWakeLock?.isHeld == false) {
                proximityWakeLock?.acquire(MAX_CALL_WAKELOCK_MS)
                logger?.log(
                    LogCategory.SYSTEM,
                    "قفل سنسور مجاورت (WakeLock) فعال شد",
                    "خاموشی خودکار صفحه هنگام قرارگیری کنار گوش"
                )
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                logger?.log(LogCategory.SYSTEM, "قفل سنسور مجاورت آزاد شد")
            }
        }
    }

    private fun registerSensorListener() {
        val sensor = proximitySensor ?: return
        runCatching {
            sensorManager?.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    private fun unregisterSensorListener() {
        runCatching {
            sensorManager?.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensor = proximitySensor ?: return
        val distance = event?.values?.firstOrNull() ?: return
        val near = distance < sensor.maximumRange
        if (near != isNearEar) {
            isNearEar = near
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val WAKELOCK_TAG = "IssabelGate::ProximityScreenOff"
        private const val MAX_CALL_WAKELOCK_MS = 2 * 60 * 60 * 1000L
    }
}
