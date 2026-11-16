package com.example.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.example.IssabelGateApp
import com.example.R
import com.example.data.model.LogCategory
import com.example.ui.call.IncomingCallActivity

class SoftphoneConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val extras = request?.extras
        val addressUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras?.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras?.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
        }
        val caller = extras?.getString(EXTRA_CALLER_NUMBER)
            ?.takeIf { it.isNotBlank() }
            ?: addressUri?.schemeSpecificPart
            ?: request?.address?.schemeSpecificPart
            ?: "SIP"
        val display = extras?.getString(EXTRA_CALLER_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: caller

        val app = application as? IssabelGateApp
        app?.logger?.log(
            LogCategory.CALL,
            "ثبت تماس ورودی در سیستم (Telecom ConnectionService)",
            "شماره: $caller | نام: $display"
        )

        val conn = SoftphoneCallConnection(
            appContext = applicationContext,
            callerNumber = caller,
            callerName = display
        ).apply {
            setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            setConnectionCapabilities(
                Connection.CAPABILITY_HOLD or
                    Connection.CAPABILITY_SUPPORT_HOLD or
                    Connection.CAPABILITY_MUTE
            )
            setAudioModeIsVoip(true)
            setAddress(
                Uri.fromParts(PhoneAccount.SCHEME_SIP, caller, null),
                TelecomManager.PRESENTATION_ALLOWED
            )
            setCallerDisplayName(display, TelecomManager.PRESENTATION_ALLOWED)
            setExtras(extras ?: Bundle())
            setInitialized()
            setRinging()
        }
        activeConnection = conn
        return conn
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val target = request?.address?.schemeSpecificPart ?: "SIP"
        val conn = SoftphoneCallConnection(
            appContext = applicationContext,
            callerNumber = target,
            callerName = target
        ).apply {
            setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            setConnectionCapabilities(
                Connection.CAPABILITY_HOLD or
                    Connection.CAPABILITY_SUPPORT_HOLD or
                    Connection.CAPABILITY_MUTE
            )
            setAudioModeIsVoip(true)
            setAddress(
                Uri.fromParts(PhoneAccount.SCHEME_SIP, target, null),
                TelecomManager.PRESENTATION_ALLOWED
            )
            setInitialized()
            setDialing()
        }
        activeConnection = conn
        return conn
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        val app = application as? IssabelGateApp
        app?.logger?.log(
            LogCategory.CALL,
            "TelecomManager امکان ثبت تماس ورودی را نداد؛ نمایش مستقیم فعال میشود",
            isError = true
        )
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    class SoftphoneCallConnection(
        private val appContext: Context,
        private val callerNumber: String,
        private val callerName: String
    ) : Connection() {

        override fun onShowIncomingCallUi() {
            val intent = Intent(appContext, IncomingCallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra(IncomingCallActivity.EXTRA_IS_INCOMING, true)
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(EXTRA_CALLER_NAME, callerName)
            }
            runCatching { appContext.startActivity(intent) }
        }

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCallAudioStateChanged(state: CallAudioState?) {
            super.onCallAudioStateChanged(state)
            val app = appContext.applicationContext as? IssabelGateApp ?: return
            if (state != null) {
                app.logger.log(
                    LogCategory.CALL,
                    "تغییر مسیر صدای سیستم (Telecom)",
                    "Route=${state.route}, Muted=${state.isMuted}"
                )
            }
        }

        override fun onAnswer() {
            setActive()
            SoftphoneForegroundService.sendAction(appContext, SoftphoneForegroundService.ACTION_ANSWER_CALL)
        }

        override fun onReject() {
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
            activeConnection = null
            SoftphoneForegroundService.sendAction(appContext, SoftphoneForegroundService.ACTION_DECLINE_CALL)
        }

        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
            activeConnection = null
            SoftphoneForegroundService.sendAction(appContext, SoftphoneForegroundService.ACTION_HANGUP_CALL)
        }

        override fun onAbort() {
            setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
            destroy()
            activeConnection = null
            SoftphoneForegroundService.sendAction(appContext, SoftphoneForegroundService.ACTION_HANGUP_CALL)
        }

        override fun onHold() {
            setOnHold()
            SoftphoneForegroundService.sendAction(appContext, SoftphoneForegroundService.ACTION_TOGGLE_HOLD)
        }

        override fun onUnhold() {
            setActive()
            SoftphoneForegroundService.sendAction(appContext, SoftphoneForegroundService.ACTION_TOGGLE_HOLD)
        }
    }

    companion object {
        const val ACCOUNT_ID = "issabel_gate_voip_account"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_CALLER_NAME = "extra_caller_name"

        @Volatile
        var activeConnection: SoftphoneCallConnection? = null

        fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
            val componentName = ComponentName(context, SoftphoneConnectionService::class.java)
            return PhoneAccountHandle(componentName, ACCOUNT_ID)
        }

        fun registerPhoneAccount(context: Context) {
            runCatching {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val handle = getPhoneAccountHandle(context)
                val builder = PhoneAccount.builder(
                    handle,
                    context.getString(R.string.connection_service_label)
                )
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setShortDescription(context.getString(R.string.app_name))
                }
                telecomManager.registerPhoneAccount(builder.build())
            }
        }

        fun notifyIncomingCall(context: Context, number: String, displayName: String) {
            runCatching {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val handle = getPhoneAccountHandle(context)
                val sipUri = Uri.fromParts(PhoneAccount.SCHEME_SIP, number, null)
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, sipUri)
                    putString(EXTRA_CALLER_NUMBER, number)
                    putString(EXTRA_CALLER_NAME, displayName)
                }
                telecomManager.addNewIncomingCall(handle, extras)
            }
        }

        fun markActive() {
            runCatching {
                activeConnection?.setActive()
            }
        }

        fun markDisconnected(cause: Int = DisconnectCause.REMOTE) {
            runCatching {
                activeConnection?.setDisconnected(DisconnectCause(cause))
                activeConnection?.destroy()
                activeConnection = null
            }
        }
    }
}
