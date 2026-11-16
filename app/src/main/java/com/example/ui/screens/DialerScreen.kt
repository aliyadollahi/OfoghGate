package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CallRecordEntity
import com.example.data.local.QuickExtensionEntity
import com.example.data.model.CallDirection
import com.example.data.model.ConnectionPathState
import com.example.data.model.NetworkSnapshot
import com.example.data.model.SoftphoneConfig
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DialerScreen(
    config: SoftphoneConfig,
    snapshot: NetworkSnapshot,
    dialDigits: String,
    quickExtensions: List<QuickExtensionEntity>,
    recentCalls: List<CallRecordEntity>,
    onDigitPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearDigits: () -> Unit,
    onCallClick: (String?) -> Unit,
    onRefreshRegister: () -> Unit,
    onAddQuickExtension: (String, String, String) -> Unit,
    onDeleteQuickExtension: (Long) -> Unit,
    onClearCallHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddExtensionDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RegistrationStatusHeader(
                    config = config,
                    snapshot = snapshot,
                    onRefreshRegister = onRefreshRegister
                )
            }

            item {
                DialpadCard(
                    dialDigits = dialDigits,
                    onDigitPress = onDigitPress,
                    onBackspace = onBackspace,
                    onClearDigits = onClearDigits,
                    onCallClick = { onCallClick(null) }
                )
            }

            item {
                QuickExtensionsStrip(
                    quickExtensions = quickExtensions,
                    onDialExtension = { ext -> onCallClick(ext) },
                    onAddClick = { showAddExtensionDialog = true },
                    onDeleteExtension = onDeleteQuickExtension
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تاریخچه تماسها",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (recentCalls.isNotEmpty()) {
                        TextButton(onClick = onClearCallHistory) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "پاک کردن تاریخچه",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("پاکسازی")
                        }
                    }
                }
            }

            if (recentCalls.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "هنوز تماسی ثبت نشده است. با شمارهگیر داخلی یا لیست سریع تماس بگیرید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(recentCalls, key = { it.id }) { call ->
                    CallHistoryRow(
                        record = call,
                        onRedial = { onCallClick(call.number) }
                    )
                }
            }
        }
    }

    if (showAddExtensionDialog) {
        AddQuickExtensionDialog(
            onDismiss = { showAddExtensionDialog = false },
            onSave = { ext, label, dept ->
                onAddQuickExtension(ext, label, dept)
                showAddExtensionDialog = false
            }
        )
    }
}

@Composable
private fun RegistrationStatusHeader(
    config: SoftphoneConfig,
    snapshot: NetworkSnapshot,
    onRefreshRegister: () -> Unit
) {
    val (dotColor, bgColor) = when (snapshot.connectionState) {
        ConnectionPathState.CONNECTED_LAN,
        ConnectionPathState.CONNECTED_WAN_NAT,
        ConnectionPathState.CONNECTED_WAN_KNOCK -> SignalEmerald to SignalEmerald.copy(alpha = 0.14f)
        ConnectionPathState.KNOCKING,
        ConnectionPathState.DETECTING,
        ConnectionPathState.REGISTERING -> SignalAmber to SignalAmber.copy(alpha = 0.14f)
        else -> SignalCrimson to SignalCrimson.copy(alpha = 0.14f)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = bgColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = snapshot.connectionState.titleFa,
                            style = MaterialTheme.typography.labelLarge,
                            color = dotColor,
                            maxLines = 1
                        )
                    }
                }
                Column {
                    Text(
                        text = if (config.extension.isNotBlank()) "داخلی ${config.extension}" else "داخلی تنظیم نشده",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = snapshot.sipStatusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onRefreshRegister,
                modifier = Modifier.testTag("dialer_refresh_register_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "تمدید رجیستر")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadCard(
    dialDigits: String,
    onDigitPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearDigits: () -> Unit,
    onCallClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val keypadRows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Number readout bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = dialDigits.ifBlank { "شماره داخلی..." },
                        style = if (dialDigits.isBlank()) {
                            MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            MonoTextStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = onBackspace,
                            onLongClick = onClearDigits
                        )
                        .testTag("dialer_backspace_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "حذف رقم"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Keep 1-2-3 keypad order strictly left-to-right
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    keypadRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { (digit, sub) ->
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDigitPress(digit)
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .size(width = 92.dp, height = 58.dp)
                                        .testTag("dialpad_key_$digit")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = digit,
                                            style = MonoTextStyle.copy(
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        if (sub.isNotEmpty()) {
                                            Text(
                                                text = sub,
                                                style = MonoTextStyle.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onCallClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalEmerald,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(54.dp)
                    .testTag("dialer_call_button")
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("برقراری تماس", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun QuickExtensionsStrip(
    quickExtensions: List<QuickExtensionEntity>,
    onDialExtension: (String) -> Unit,
    onAddClick: () -> Unit,
    onDeleteExtension: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("داخلیهای سریع سازمان", style = MaterialTheme.typography.titleMedium)
            TextButton(
                onClick = onAddClick,
                modifier = Modifier.testTag("add_quick_extension_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("افزودن داخلی")
            }
        }

        if (quickExtensions.isEmpty()) {
            Surface(
                onClick = onAddClick,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "برای تماس یکلمسهای با همکاران، داخلیهای پرکاربرد را اضافه کنید.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(quickExtensions, key = { it.id }) { item ->
                    Surface(
                        onClick = { onDialExtension(item.extension) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.widthIn(min = 128.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(item.label, style = MaterialTheme.typography.labelLarge)
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = item.extension,
                                        style = MonoTextStyle.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeleteExtension(item.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "حذف",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(
    record: CallRecordEntity,
    onRedial: () -> Unit
) {
    val direction = runCatching { CallDirection.valueOf(record.direction) }
        .getOrDefault(CallDirection.OUTGOING)
    val (dirIcon, dirColor) = when (direction) {
        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to SignalEmerald
        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.colorScheme.primary
        CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed to SignalCrimson
    }
    val formatter = remember { SimpleDateFormat("HH:mm  yyyy/MM/dd", Locale.US) }

    Surface(
        onClick = onRedial,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(dirIcon, contentDescription = direction.labelFa, tint = dirColor)
                Column {
                    Text(record.displayName, style = MaterialTheme.typography.titleMedium)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "${record.number} • ${formatter.format(Date(record.timestampMs))}",
                            style = MonoTextStyle.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val m = record.durationSeconds / 60
                    val s = record.durationSeconds % 60
                    Text(
                        text = "%02d:%02d".format(m, s),
                        style = MonoTextStyle.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (record.networkModeUsed.isNotBlank()) {
                    Text(
                        text = record.networkModeUsed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AddQuickExtensionDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var ext by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن داخلی سریع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = ext,
                    onValueChange = { ext = it },
                    label = { Text("شماره داخلی (مثلاً 102)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("نام همکار یا بخش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dept,
                    onValueChange = { dept = it },
                    label = { Text("واحد (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(ext, label, dept) },
                enabled = ext.isNotBlank()
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
