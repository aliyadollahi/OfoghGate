package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DiagnosticLogEntry
import com.example.data.model.LogCategory
import com.example.ui.components.AndroidSourcePanelDialog
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    logs: List<DiagnosticLogEntry>,
    isBatteryOptimizedIgnored: Boolean,
    canScheduleExactAlarms: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onRequestFullScreenIntent: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<LogCategory?>(null) }
    var showSourcePanel by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, selectedCategory) {
        if (selectedCategory == null) logs else logs.filter { it.category == selectedCategory }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BackgroundHealthCard(
                    isBatteryOptimizedIgnored = isBatteryOptimizedIgnored,
                    canScheduleExactAlarms = canScheduleExactAlarms,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                    onRequestExactAlarms = onRequestExactAlarms,
                    onRequestFullScreenIntent = onRequestFullScreenIntent,
                    onOpenSourcePanel = { showSourcePanel = true }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("لاگ زنده سیستم و ناکینگ", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val text = filteredLogs.joinToString("\n") { entry ->
                                    "[${entry.category.labelFa}] ${entry.message} ${entry.detail}"
                                }
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("IssabelGateLogs", text))
                                Toast.makeText(context, "لاگها کپی شدند", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("کپی")
                        }
                        OutlinedButton(
                            onClick = onClearLogs,
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("پاک")
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("همه (${logs.size})") }
                        )
                    }
                    items(LogCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.labelFa) }
                        )
                    }
                }
            }

            if (filteredLogs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "هنوز رویدادی در این دسته ثبت نشده است.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(filteredLogs, key = { it.id }) { entry ->
                    LogEntryCard(entry = entry)
                }
            }
        }
    }

    if (showSourcePanel) {
        AndroidSourcePanelDialog(onDismiss = { showSourcePanel = false })
    }
}

@Composable
private fun BackgroundHealthCard(
    isBatteryOptimizedIgnored: Boolean,
    canScheduleExactAlarms: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onRequestFullScreenIntent: () -> Unit,
    onOpenSourcePanel: () -> Unit
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
            Text("پایداری پسزمینه و مجوزهای بحرانی", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = if (isBatteryOptimizedIgnored) SignalEmerald else SignalAmber
                    )
                    Text(
                        if (isBatteryOptimizedIgnored) "بهینهسازی باتری معاف است" else "معافیت باتری فعال نشده",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!isBatteryOptimizedIgnored) {
                    OutlinedButton(onClick = onRequestBatteryExemption) {
                        Text("فعالسازی")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (canScheduleExactAlarms) SignalEmerald else SignalAmber
                    )
                    Text(
                        if (canScheduleExactAlarms) "تایمر دقیق (Doze) فعال است" else "نیاز به مجوز Exact Alarm",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!canScheduleExactAlarms) {
                    OutlinedButton(onClick = onRequestExactAlarms) {
                        Text("تنظیم")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRequestFullScreenIntent,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مجوز تماس تمامصفحه")
                }

                Button(
                    onClick = onOpenSourcePanel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_android_source_panel_button")
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("پنل فنی و سورس")
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: DiagnosticLogEntry) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val catColor = when {
        entry.isError -> SignalCrimson
        entry.category == LogCategory.KNOCK -> SignalCyan
        entry.category == LogCategory.SIP -> SignalEmerald
        entry.category == LogCategory.NETWORK -> SignalAmber
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (entry.isError) SignalCrimson.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = catColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = entry.category.labelFa,
                        style = MaterialTheme.typography.labelSmall,
                        color = catColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = timeFormatter.format(Date(entry.timestampMs)),
                        style = MonoTextStyle.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(entry.message, style = MaterialTheme.typography.bodyLarge)
            if (entry.detail.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = entry.detail,
                        style = MonoTextStyle.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
