package com.example.engine

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.example.R

class SoftphoneConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val conn = SoftphoneCallConnection(applicationContext).apply {
            setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            setAudioModeIsVoip(true)
            val caller = request?.extras?.getString(EXTRA_CALLER_NUMBER) ?: "SIP"
            val display = request?.extras?.getString(EXTRA_CALLER_NAME) ?: caller
            setAddress(Uri.fromParts("sip", caller, null), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(display, TelecomManager.PRESENTATION_ALLOWED)
            setRinging()
        }
        activeConnection = conn
        return conn
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val conn = SoftphoneCallConnection(applicationContext).apply {
            setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            setAudioModeIsVoip(true)
            val target = request?.address?.schemeSpecificPart ?: "SIP"
            setAddress(Uri.fromParts("sip", target, null), TelecomManager.PRESENTATION_ALLOWED)
            setDialing()
        }
        activeConnection = conn
        return conn
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    class SoftphoneCallConnection(private val appContext: Context) : Connection() {
        override fun onAnswer() {
            setActive()
            SoftphoneForegroundService.sendAction(appContext, SoftphoneForegroundService.ACTION_ANSWER_CALL)
        }

        override fun onReject() {
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
            SoftphoneForegroundService.sendAction(appContext, SoftphoneForegroundService.ACTION_DECLINE_CALL)
        }

        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
            SoftphoneForegroundService.sendAction(appContext, SoftphoneForegroundService.ACTION_HANGUP_CALL)
        }

        override fun onAbort() {
            setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
            destroy()
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
                    .addSupportedUriScheme("sip")
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
                val extras = Bundle().apply {
                    putString(EXTRA_CALLER_NUMBER, number)
                    putString(EXTRA_CALLER_NAME, displayName)
                }
                telecomManager.addNewIncomingCall(handle, extras)
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
