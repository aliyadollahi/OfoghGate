package com.example.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.EncryptedConfigStore

class BootAndAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val store = EncryptedConfigStore(context)
                val config = store.loadConfig()
                if (config.serviceAutoStartOnBoot && config.isConfigured()) {
                    runCatching {
                        SoftphoneForegroundService.startOrUpdate(context)
                    }
                }
            }

            ACTION_SCHEDULED_KNOCK -> {
                runCatching {
                    SoftphoneForegroundService.sendAction(
                        context,
                        SoftphoneForegroundService.ACTION_SCHEDULED_KNOCK_FIRE
                    )
                }
            }

            ACTION_CALL_ANSWER -> {
                runCatching {
                    SoftphoneForegroundService.sendAction(
                        context,
                        SoftphoneForegroundService.ACTION_ANSWER_CALL
                    )
                }
            }

            ACTION_CALL_DECLINE -> {
                runCatching {
                    SoftphoneForegroundService.sendAction(
                        context,
                        SoftphoneForegroundService.ACTION_DECLINE_CALL
                    )
                }
            }

            ACTION_CALL_HANGUP -> {
                runCatching {
                    SoftphoneForegroundService.sendAction(
                        context,
                        SoftphoneForegroundService.ACTION_HANGUP_CALL
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULED_KNOCK = "com.example.ACTION_SCHEDULED_KNOCK"
        const val ACTION_CALL_ANSWER = "com.example.ACTION_CALL_ANSWER"
        const val ACTION_CALL_DECLINE = "com.example.ACTION_CALL_DECLINE"
        const val ACTION_CALL_HANGUP = "com.example.ACTION_CALL_HANGUP"
    }
}
