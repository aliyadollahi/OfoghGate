package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.KnockProtocol
import com.example.data.model.KnockStep
import com.example.data.model.SoftphoneConfig
import com.example.data.model.TransportType
import com.example.data.provisioning.ProvisionDecodeResult
import com.example.ui.components.AndroidSourcePanelDialog
import com.example.ui.components.QrScannerDialog
import com.example.data.provisioning.RouterOsVersion

@Composable
fun SettingsAdminScreen(
    config: SoftphoneConfig,
    onSaveConfig: (SoftphoneConfig) -> Unit,
    onDecodeProvision: (String, String?) -> ProvisionDecodeResult,
    onEncodeProvision: (SoftphoneConfig, String?) -> String,
    onBuildMikroTikScript: (SoftphoneConfig, RouterOsVersion) -> String,
    modifier: Modifier = Modifier
) {
    var draft by remember(config) { mutableStateOf(config) }
    var showPassword by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pendingEncryptedPayload by remember { mutableStateOf<String?>(null) }
    var pinDialogError by remember { mutableStateOf<String?>(null) }
    var showSourcePanel by remember { mutableStateOf(false) }

    LaunchedEffect(config) {
        draft = config
    }

    fun handleIncomingProvisionString(raw: String, pin: String? = null) {
        when (val res = onDecodeProvision(raw, pin)) {
            is ProvisionDecodeResult.Success -> {
                draft = res.config
                pendingEncryptedPayload = null
                pinDialogError = null
                showPasteDialog = false
            }
            ProvisionDecodeResult.PinRequired -> {
                pendingEncryptedPayload = raw
                pinDialogError = null
            }
            ProvisionDecodeResult.WrongPinOrCorrupted -> {
                pinDialogError = "پین نادرست است یا رشته خراب میباشد."
            }
            ProvisionDecodeResult.InvalidFormat -> {
                pinDialogError = "فرمت رشته ISSABEL-GATE:// نامعتبر است."
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quick Import Card (QR + Paste String)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("ورود سریع تنظیمات سازمانی", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showQrScanner = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_qr_scanner_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اسکن QR")
                            }
                            OutlinedButton(
                                onClick = { showPasteDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("paste_provision_button")
                            ) {
                                Icon(Icons.Default.ContentPasteGo, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جایگذاری رشته")
                            }
                        }
                    }
                }
            }

            // SIP Account Credentials
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("حساب کاربری SIP (ایزابل)", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LtrOutlinedField(
                                value = draft.extension,
                                onValueChange = { draft = draft.copy(extension = it) },
                                label = "شماره داخلی (Extension)",
                                modifier = Modifier.weight(1f),
                                testTag = "settings_extension_input"
                            )
                            LtrOutlinedField(
                                value = draft.authUser,
                                onValueChange = { draft = draft.copy(authUser = it) },
                                label = "Auth User (اختیاری)",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = draft.displayName,
                            onValueChange = { draft = draft.copy(displayName = it) },
                            label = { Text("نام نمایشی") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LtrOutlinedField(
                            value = draft.password,
                            onValueChange = { draft = draft.copy(password = it) },
                            label = "رمز عبور داخلی (SIP Secret)",
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "نمایش رمز"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "settings_password_input"
                        )
                    }
                }
            }

            // Server LAN/WAN & Transport & Codecs
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("سرور ایزابل (LAN و WAN) و پروتکل", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LtrOutlinedField(
                                value = draft.lanHost,
                                onValueChange = { draft = draft.copy(lanHost = it) },
                                label = "IP داخلی (LAN)",
                                modifier = Modifier.weight(2f)
                            )
                            LtrOutlinedField(
                                value = draft.lanPort.toString(),
                                onValueChange = { v ->
                                    draft = draft.copy(lanPort = v.filter(Char::isDigit).toIntOrNull() ?: 5060)
                                },
                                label = "پورت LAN",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        LtrOutlinedField(
                            value = draft.officeWifiSsid,
                            onValueChange = { draft = draft.copy(officeWifiSsid = it) },
                            label = "نام Wi-Fi سازمان (SSID - اختیاری)",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LtrOutlinedField(
                                value = draft.wanHost,
                                onValueChange = { draft = draft.copy(wanHost = it) },
                                label = "IP استاتیک / دامنه WAN",
                                modifier = Modifier.weight(2f)
                            )
                            LtrOutlinedField(
                                value = draft.wanPort.toString(),
                                onValueChange = { v ->
                                    draft = draft.copy(wanPort = v.filter(Char::isDigit).toIntOrNull() ?: 5060)
                                },
                                label = "پورت WAN",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text("نوع انتقال SIP (Transport)", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TransportType.entries.forEach { t ->
                                FilterChip(
                                    selected = draft.transport == t,
                                    onClick = { draft = draft.copy(transport = t) },
                                    label = { Text(t.label) }
                                )
                            }
                        }

                        Text("کدکهای صوتی فعال", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CodecCheck("Opus", draft.enableOpus) { draft = draft.copy(enableOpus = it) }
                            CodecCheck("PCMU", draft.enablePcmu) { draft = draft.copy(enablePcmu = it) }
                            CodecCheck("PCMA", draft.enablePcma) { draft = draft.copy(enablePcma = it) }
                        }
                    }
                }
            }

            // MikroTik Port Knocking Settings
            item {
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
                            Text("تنظیمات پورت ناکینگ (۱ تا ۶ پورت)", style = MaterialTheme.typography.titleMedium)
                            if (draft.knockSteps.size < 6) {
                                OutlinedButton(
                                    onClick = {
                                        val nextPort = (draft.knockSteps.lastOrNull()?.port ?: 10000) + 111
                                        draft = draft.copy(
                                            knockSteps = draft.knockSteps + KnockStep(nextPort, KnockProtocol.UDP, 180)
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("پورت")
                                }
                            }
                        }

                        LtrOutlinedField(
                            value = draft.knockTargetHost,
                            onValueChange = { draft = draft.copy(knockTargetHost = it) },
                            label = "آدرس روتر میکروتیک (اگر خالی باشد از WAN استفاده میشود)",
                            modifier = Modifier.fillMaxWidth()
                        )

                        draft.knockSteps.forEachIndexed { idx, step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LtrOutlinedField(
                                    value = step.port.toString(),
                                    onValueChange = { v ->
                                        val p = v.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 65535) ?: step.port
                                        val updated = draft.knockSteps.toMutableList()
                                        updated[idx] = step.copy(port = p)
                                        draft = draft.copy(knockSteps = updated)
                                    },
                                    label = "پورت ${idx + 1}",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1.2f)
                                )
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        val nextProto = if (step.protocol == KnockProtocol.UDP) KnockProtocol.TCP else KnockProtocol.UDP
                                        val updated = draft.knockSteps.toMutableList()
                                        updated[idx] = step.copy(protocol = nextProto)
                                        draft = draft.copy(knockSteps = updated)
                                    },
                                    label = { Text(step.protocol.label) }
                                )
                                LtrOutlinedField(
                                    value = step.delayMs.toString(),
                                    onValueChange = { v ->
                                        val d = v.filter(Char::isDigit).toIntOrNull() ?: step.delayMs
                                        val updated = draft.knockSteps.toMutableList()
                                        updated[idx] = step.copy(delayMs = d)
                                        draft = draft.copy(knockSteps = updated)
                                    },
                                    label = "تاخیرms",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f)
                                )
                                if (draft.knockSteps.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val updated = draft.knockSteps.toMutableList()
                                            updated.removeAt(idx)
                                            draft = draft.copy(knockSteps = updated)
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف مرحله")
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LtrOutlinedField(
                                value = draft.knockIntervalMinutes.toString(),
                                onValueChange = { v ->
                                    draft = draft.copy(knockIntervalMinutes = (v.filter(Char::isDigit).toIntOrNull() ?: 15).coerceIn(1, 180))
                                },
                                label = "تکرار ناک (دقیقه)",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            LtrOutlinedField(
                                value = draft.preCallKnockGuardSeconds.toString(),
                                onValueChange = { v ->
                                    draft = draft.copy(preCallKnockGuardSeconds = (v.filter(Char::isDigit).toIntOrNull() ?: 600).coerceIn(30, 7200))
                                },
                                label = "محافظ پیش از تماس (ثانیه)",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("اجرای خودکار پس از روشن شدن گوشی", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = draft.serviceAutoStartOnBoot,
                                onCheckedChange = { draft = draft.copy(serviceAutoStartOnBoot = it) }
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { onSaveConfig(draft) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_settings_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ذخیره تنظیمات و اتصال به ایزابل", style = MaterialTheme.typography.titleMedium)
                }
            }

            item {
                AdminWorkshopCard(
                    config = draft,
                    onEncodeProvision = onEncodeProvision,
                    onBuildMikroTikScript = onBuildMikroTikScript,
                    onOpenSourcePanel = { showSourcePanel = true }
                )
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onScanned = { scanned ->
                showQrScanner = false
                handleIncomingProvisionString(scanned)
            },
            onDismiss = { showQrScanner = false }
        )
    }

    if (showPasteDialog) {
        PasteProvisionDialog(
            errorMessage = pinDialogError,
            onDismiss = {
                showPasteDialog = false
                pinDialogError = null
            },
            onApply = { raw -> handleIncomingProvisionString(raw) }
        )
    }

    if (pendingEncryptedPayload != null) {
        PinDecryptDialog(
            errorMessage = pinDialogError,
            onDismiss = {
                pendingEncryptedPayload = null
                pinDialogError = null
            },
            onConfirmPin = { pin ->
                handleIncomingProvisionString(pendingEncryptedPayload.orEmpty(), pin)
            }
        )
    }

    if (showSourcePanel) {
        AndroidSourcePanelDialog(onDismiss = { showSourcePanel = false })
    }
}

@Composable
private fun LtrOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    testTag: String? = null
) {
    val fieldMod = if (testTag != null) modifier.testTag(testTag) else modifier
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            modifier = fieldMod
        )
    }
}

@Composable
private fun CodecCheck(
    name: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(name, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PasteProvisionDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var payload by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جایگذاری رشته تنظیمات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "رشته با پیشوند ISSABEL-GATE:// را وارد کنید:",
                    style = MaterialTheme.typography.bodyMedium
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = { Text("ISSABEL-GATE://v1...") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(payload) }, enabled = payload.isNotBlank()) {
                Text("اعمال")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun PinDecryptDialog(
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirmPin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رمزگشایی فایل تنظیمات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("این پیکربندی با AES-GCM رمزنگاری شده است. پین مدیر را وارد کنید:")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("پین رمزگشایی") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmPin(pin) }, enabled = pin.isNotBlank()) {
                Text("رمزگشایی و ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
