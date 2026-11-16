package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionPathState
import com.example.data.model.NetworkMode
import com.example.data.model.NetworkSnapshot
import com.example.data.model.SoftphoneConfig
import com.example.data.model.StepRunState
import com.example.ui.ProbeSummary
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import kotlinx.coroutines.delay

@Composable
fun NetworkKnockScreen(
    config: SoftphoneConfig,
    snapshot: NetworkSnapshot,
    probeSummary: ProbeSummary,
    onSelectNetworkMode: (NetworkMode) -> Unit,
    onKnockNow: () -> Unit,
    onRunProbes: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snapshot.nextKnockEpochMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LiveConnectionHeroCard(
                    config = config,
                    snapshot = snapshot,
                    nowMs = nowMs,
                    onKnockNow = onKnockNow
                )
            }

            item {
                KnockSequenceStatusCard(config = config, snapshot = snapshot)
            }

            item {
                NetworkModeSelectorCard(
                    selectedMode = config.networkMode,
                    onSelectMode = onSelectNetworkMode
                )
            }

            item {
                HostProbeCard(
                    config = config,
                    probeSummary = probeSummary,
                    onRunProbes = onRunProbes
                )
            }
        }
    }
}

@Composable
private fun LiveConnectionHeroCard(
    config: SoftphoneConfig,
    snapshot: NetworkSnapshot,
    nowMs: Long,
    onKnockNow: () -> Unit
) {
    val accent = when (snapshot.connectionState) {
        ConnectionPathState.CONNECTED_LAN,
        ConnectionPathState.CONNECTED_WAN_NAT,
        ConnectionPathState.CONNECTED_WAN_KNOCK -> SignalEmerald
        ConnectionPathState.KNOCKING,
        ConnectionPathState.DETECTING,
        ConnectionPathState.REGISTERING -> SignalAmber
        else -> SignalCrimson
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            accent.copy(alpha = 0.18f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Column {
                            Text(
                                text = snapshot.connectionState.titleFa,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مسیر فعال: ${snapshot.activePath.labelFa}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Telemetry chips row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMiniTile(
                        label = "شبکه فعلی",
                        value = snapshot.transportLabel,
                        isMono = false,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMiniTile(
                        label = "IP دستگاه",
                        value = snapshot.localIp,
                        isMono = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Countdown to next Doze-resistant knock
                val remainingSec = if (snapshot.nextKnockEpochMs > nowMs) {
                    (snapshot.nextKnockEpochMs - nowMs) / 1000L
                } else 0L

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("شمارش معکوس ناک بعدی", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "بازه تنظیمشده: ${config.knockIntervalMinutes} دقیقه",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            val min = remainingSec / 60
                            val sec = remainingSec % 60
                            Text(
                                text = if (snapshot.nextKnockEpochMs > 0L) "%02d:%02d".format(min, sec) else "--:--",
                                style = MonoTextStyle.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalCyan
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = onKnockNow,
                    enabled = !snapshot.knockInProgress,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("knock_now_button")
                ) {
                    if (snapshot.knockInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال ارسال بستههای ناک...")
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ارسال ناک فوری و تمدید رجیستر")
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniTile(
    label: String,
    value: String,
    isMono: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            if (isMono) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(value, style = MonoTextStyle, maxLines = 1)
                }
            } else {
                Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun KnockSequenceStatusCard(
    config: SoftphoneConfig,
    snapshot: NetworkSnapshot
) {
    val stepStatuses = snapshot.stepStatuses
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("توالی پورت ناکینگ میکروتیک", style = MaterialTheme.typography.titleMedium)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = config.effectiveKnockHost().ifBlank { "هدف نامشخص" },
                        style = MonoTextStyle.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            config.knockSteps.forEachIndexed { idx, step ->
                val runStatus = stepStatuses.getOrNull(idx)
                val badgeColor = when (runStatus?.state) {
                    StepRunState.SENT -> SignalEmerald
                    StepRunState.SENDING -> SignalAmber
                    StepRunState.FAILED -> SignalCrimson
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (runStatus?.state) {
                                StepRunState.SENT -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalEmerald, modifier = Modifier.size(18.dp))
                                StepRunState.SENDING -> Icon(Icons.Default.Sync, contentDescription = null, tint = SignalAmber, modifier = Modifier.size(18.dp))
                                StepRunState.FAILED -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = SignalCrimson, modifier = Modifier.size(18.dp))
                                else -> Icon(Icons.Default.Router, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
                            }
                            Text("مرحله ${idx + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            val latency = if ((runStatus?.latencyMs ?: 0L) > 0) " • ${runStatus?.latencyMs}ms" else ""
                            Text(
                                text = "${step.protocol.label} : ${step.port} (${step.delayMs}ms)$latency",
                                style = MonoTextStyle.copy(fontSize = 13.sp),
                                color = badgeColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkModeSelectorCard(
    selectedMode: NetworkMode,
    onSelectMode: (NetworkMode) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("حالت اتصال شبکه (Network Mode)", style = MaterialTheme.typography.titleMedium)
            NetworkMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                Surface(
                    onClick = { onSelectMode(mode) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("network_mode_${mode.name}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelectMode(mode) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.titleFa, style = MaterialTheme.typography.labelLarge)
                            Text(
                                mode.subtitleFa,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostProbeCard(
    config: SoftphoneConfig,
    probeSummary: ProbeSummary,
    onRunProbes: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تست دسترسی و پینگ ایزابل", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onRunProbes,
                    enabled = !probeSummary.isRunning,
                    modifier = Modifier.testTag("run_host_probe_button")
                ) {
                    Icon(Icons.Default.NetworkPing, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (probeSummary.isRunning) "در حال بررسی..." else "شروع تست")
                }
            }

            probeSummary.lanResult?.let { lan ->
                ProbeRowItem(
                    icon = Icons.Default.Lan,
                    title = "LAN (${config.lanHost}:${config.lanPort})",
                    reachable = lan.reachable,
                    detail = "${lan.detail} (${lan.rttMs}ms)"
                )
            }
            probeSummary.wanResult?.let { wan ->
                ProbeRowItem(
                    icon = Icons.Default.Public,
                    title = "WAN (${config.wanHost}:${config.wanPort})",
                    reachable = wan.reachable,
                    detail = "${wan.detail} (${wan.rttMs}ms)"
                )
            }
            if (probeSummary.lanResult == null && probeSummary.wanResult == null) {
                Text(
                    "برای بررسی در دسترس بودن پورت SIP داخلی و خارجی دکمه شروع تست را بزنید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProbeRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    reachable: Boolean,
    detail: String
) {
    val color = if (reachable) SignalEmerald else SignalCrimson
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(title, style = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
    }
}
