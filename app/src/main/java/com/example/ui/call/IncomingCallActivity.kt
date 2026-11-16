package com.example.ui.call

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.IssabelGateApp
import com.example.data.model.ActiveCallState
import com.example.data.model.CallUiState
import com.example.engine.SoftphoneForegroundService
import com.example.ui.theme.IssabelGateTheme
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import kotlinx.coroutines.delay

class IncomingCallActivity : ComponentActivity() {

    private var proximityWakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenBehavior()
        enableEdgeToEdge()

        val app = application as IssabelGateApp

        setContent {
            IssabelGateTheme(darkTheme = true) {
                val callState by app.sipManager.callUiState.collectAsStateWithLifecycle()
                val snapshot by SoftphoneForegroundService.snapshot.collectAsStateWithLifecycle()

                LaunchedEffect(callState.state) {
                    when (callState.state) {
                        ActiveCallState.CONNECTED,
                        ActiveCallState.OUTGOING_RINGING,
                        ActiveCallState.HELD -> acquireProximityLock()

                        ActiveCallState.ENDED,
                        ActiveCallState.IDLE -> {
                            releaseProximityLock()
                            if (callState.state == ActiveCallState.ENDED) {
                                delay(900)
                                finish()
                            }
                        }

                        else -> Unit
                    }
                }

                CallFullScreenUi(
                    callState = callState,
                    routeLabel = snapshot.activePath.labelFa,
                    onAnswer = {
                        requestKeyguardDismiss()
                        SoftphoneForegroundService.sendAction(
                            this,
                            SoftphoneForegroundService.ACTION_ANSWER_CALL
                        )
                    },
                    onDecline = {
                        SoftphoneForegroundService.sendAction(
                            this,
                            SoftphoneForegroundService.ACTION_DECLINE_CALL
                        )
                        finish()
                    },
                    onHangup = {
                        SoftphoneForegroundService.sendAction(
                            this,
                            SoftphoneForegroundService.ACTION_HANGUP_CALL
                        )
                        finish()
                    },
                    onToggleMute = { app.sipManager.toggleMute() },
                    onToggleHold = { app.sipManager.toggleHold() },
                    onToggleSpeaker = { app.sipManager.toggleSpeaker() },
                    onToggleBluetooth = { app.sipManager.toggleBluetooth() },
                    onSendDtmf = { digit -> app.sipManager.sendDtmf(digit) }
                )
            }
        }
    }

    private fun configureLockScreenBehavior() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestKeyguardDismiss() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, null)
        }
    }

    private fun acquireProximityLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (proximityWakeLock == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = pm.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "IssabelGate::CallProximity"
                )
            }
            if (proximityWakeLock?.isHeld == false) {
                proximityWakeLock?.acquire(60 * 60 * 1000L)
            }
        }
    }

    private fun releaseProximityLock() {
        runCatching {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
            }
        }
    }

    override fun onDestroy() {
        releaseProximityLock()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_IS_INCOMING = "extra_is_incoming"
    }
}

@Composable
private fun CallFullScreenUi(
    callState: CallUiState,
    routeLabel: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleHold: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleBluetooth: () -> Unit,
    onSendDtmf: (Char) -> Unit
) {
    var showDtmfPad by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "ringPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF040D16), Color(0xFF0A2238), Color(0xFF07131E))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top route & codec pill
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0x2638BDF8)
            ) {
                Text(
                    text = "$routeLabel • ${callState.codecName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = SignalCyan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Caller Info & Avatar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (callState.state == ActiveCallState.INCOMING_RINGING ||
                        callState.state == ActiveCallState.OUTGOING_RINGING
                    ) {
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .scale(pulseScale)
                                .background(Color(0x2206B6D4), CircleShape)
                        )
                    }
                    Surface(
                        modifier = Modifier.size(104.dp),
                        shape = CircleShape,
                        color = Color(0xFF12304A)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "تصویر مخاطب",
                                tint = SignalCyan,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                }

                Text(
                    text = callState.remoteName.ifBlank { "تماس ایزابل" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = callState.remoteNumber.ifBlank { "---" },
                        style = MonoTextStyle.copy(fontSize = 20.sp),
                        color = Color(0xFFCBD5E1)
                    )
                }

                if (callState.state == ActiveCallState.CONNECTED || callState.state == ActiveCallState.HELD) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = formatDuration(callState.durationSeconds),
                            style = MonoTextStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                            color = SignalEmerald
                        )
                    }
                } else {
                    Text(
                        text = callState.statusDetail.ifBlank { "تماس صوتی سازمانی" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Optional In-Call DTMF Pad
            AnimatedVisibility(visible = showDtmfPad && callState.state != ActiveCallState.INCOMING_RINGING) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC0D1D2E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val rows = listOf(
                                listOf('1', '2', '3'),
                                listOf('4', '5', '6'),
                                listOf('7', '8', '9'),
                                listOf('*', '0', '#')
                            )
                            rows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    row.forEach { ch ->
                                        Surface(
                                            onClick = { onSendDtmf(ch) },
                                            shape = CircleShape,
                                            color = Color(0xFF1A3248),
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = ch.toString(),
                                                    style = MonoTextStyle.copy(fontSize = 20.sp),
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Controls
            if (callState.state == ActiveCallState.INCOMING_RINGING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onDecline,
                            modifier = Modifier
                                .size(76.dp)
                                .testTag("incoming_decline_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = SignalCrimson,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "رد تماس",
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("رد تماس", color = Color(0xFFFCA5A5), style = MaterialTheme.typography.labelLarge)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onAnswer,
                            modifier = Modifier
                                .size(76.dp)
                                .testTag("incoming_answer_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = SignalEmerald,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "پاسخ به تماس",
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("پاسخ", color = Color(0xFF6EE7B7), style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallToggleControl(
                            icon = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = "بیصدا",
                            active = callState.isMuted,
                            onClick = onToggleMute,
                            testTag = "call_mute_button"
                        )
                        CallToggleControl(
                            icon = if (callState.isHeld) Icons.Default.PlayArrow else Icons.Default.Pause,
                            label = if (callState.isHeld) "ادامه" else "انتظار",
                            active = callState.isHeld,
                            onClick = onToggleHold,
                            testTag = "call_hold_button"
                        )
                        CallToggleControl(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "بلندگو",
                            active = callState.isSpeakerOn,
                            onClick = onToggleSpeaker,
                            testTag = "call_speaker_button"
                        )
                        CallToggleControl(
                            icon = Icons.Default.Dialpad,
                            label = "کیپد",
                            active = showDtmfPad,
                            onClick = { showDtmfPad = !showDtmfPad },
                            testTag = "call_dtmf_button"
                        )
                        if (callState.isBluetoothAvailable) {
                            CallToggleControl(
                                icon = Icons.Default.BluetoothAudio,
                                label = "بلوتوث",
                                active = callState.isBluetoothOn,
                                onClick = onToggleBluetooth,
                                testTag = "call_bt_button"
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = onHangup,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("active_hangup_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SignalCrimson,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "قطع تماس",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallToggleControl(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .testTag(testTag),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) SignalCyan else Color(0xFF1A3248),
                contentColor = if (active) Color(0xFF04131E) else Color.White
            )
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}
