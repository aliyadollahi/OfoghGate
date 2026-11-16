package com.example

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ProximitySensorManager
import com.example.engine.SoftphoneConnectionService
import com.example.engine.SoftphoneForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ایزابل گیت", appName)
    }

    @Test
    fun `foreground service creates IMPORTANCE_HIGH service notification channel`() {
        val serviceController = Robolectric.buildService(SoftphoneForegroundService::class.java).create()
        val service = serviceController.get()
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(SoftphoneForegroundService.CHANNEL_SERVICE)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        serviceController.destroy()
    }

    @Test
    fun `connection service registers incoming SIP call in ringing state`() {
        val service = Robolectric.buildService(SoftphoneConnectionService::class.java).create().get()
        val handle = SoftphoneConnectionService.getPhoneAccountHandle(service)
        val extras = Bundle().apply {
            putString(SoftphoneConnectionService.EXTRA_CALLER_NUMBER, "108")
            putString(SoftphoneConnectionService.EXTRA_CALLER_NAME, "فروش")
        }
        val request = ConnectionRequest(
            handle,
            Uri.fromParts(PhoneAccount.SCHEME_SIP, "108", null),
            extras
        )

        val connection = service.onCreateIncomingConnection(handle, request)
        assertEquals(Connection.STATE_RINGING, connection.state)
        assertTrue((connection.connectionProperties and Connection.PROPERTY_SELF_MANAGED) != 0)
        assertEquals("فروش", connection.callerDisplayName)
        assertEquals("108", connection.address.schemeSpecificPart)
    }

    @Test
    fun `proximity sensor manager manages active call and speaker transitions safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ProximitySensorManager(context)
        manager.startForActiveCall(speakerOn = false)
        manager.updateSpeakerState(speakerOn = true)
        assertFalse(manager.isWakeLockHeld())
        manager.stop()
        assertFalse(manager.isWakeLockHeld())
    }
}
