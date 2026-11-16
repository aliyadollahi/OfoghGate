package com.example.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.data.model.LogCategory

class KnockAlarmScheduler(
    private val context: Context,
    private val logger: DiagnosticLogger
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleNextKnock(intervalMinutes: Int): Long {
        val delayMs = intervalMinutes.coerceIn(1, 180) * 60_000L
        val triggerElapsedMs = SystemClock.elapsedRealtime() + delayMs
        val targetEpochMs = System.currentTimeMillis() + delayMs
        val pendingIntent = buildPendingIntent()

        alarmManager.cancel(pendingIntent)

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsedMs,
                pendingIntent
            )
            logger.log(
                LogCategory.KNOCK,
                "تایمر دقیق مقاوم در برابر Doze تنظیم شد",
                "تکرار بعدی: $intervalMinutes دقیقه دیگر"
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsedMs,
                pendingIntent
            )
            logger.log(
                LogCategory.KNOCK,
                "تایمر AllowWhileIdle (غیردقیق) تنظیم شد",
                "برای دقت بیشتر در Doze مجوز Exact Alarm را فعال کنید"
            )
        }
        return targetEpochMs
    }

    fun cancelScheduledKnock() {
        alarmManager.cancel(buildPendingIntent())
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, BootAndAlarmReceiver::class.java).apply {
            action = BootAndAlarmReceiver.ACTION_SCHEDULED_KNOCK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_KNOCK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REQUEST_CODE_KNOCK = 4401
    }
}
