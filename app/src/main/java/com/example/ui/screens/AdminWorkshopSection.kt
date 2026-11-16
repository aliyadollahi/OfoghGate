package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SoftphoneConfig
import com.example.data.provisioning.QrCodeGenerator
import com.example.data.provisioning.RouterOsVersion
import com.example.ui.theme.MonoTextStyle

@Composable
fun AdminWorkshopCard(
    config: SoftphoneConfig,
    onEncodeProvision: (SoftphoneConfig, String?) -> String,
    onBuildMikroTikScript: (SoftphoneConfig, RouterOsVersion) -> String,
    onOpenSourcePanel: () -> Unit
) {
    val context = LocalContext.current
    var isUnlocked by remember { mutableStateOf(false) }
    var pinAttempt by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    var encryptProvision by remember { mutableStateOf(true) }
    var provisionPin by remember { mutableStateOf("2026") }
    var rosVersion by remember { mutableStateOf(RouterOsVersion.V7) }

    val provisionPayload = remember(config, encryptProvision, provisionPin, isUnlocked) {
        if (!isUnlocked) "" else {
            onEncodeProvision(config, if (encryptProvision) provisionPin.ifBlank { "2026" } else null)
        }
    }
    val qrBitmap = remember(provisionPayload) {
        if (provisionPayload.isBlank()) null else QrCodeGenerator.generateBitmap(provisionPayload, 520)
    }
    val mikrotikScript = remember(config, rosVersion, isUnlocked) {
        if (!isUnlocked) "" else onBuildMikroTikScript(config, rosVersion)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("کارگاه مدیر شبکه (Admin Workshop)", style = MaterialTheme.typography.titleMedium)
                }
                Icon(
                    imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (!isUnlocked) {
                Text(
                    "برای تولید QR Code پیکربندی سازمانی و اسکریپت میکروتیک، پین مدیر شبکه را وارد کنید (پیشفرض: 1234).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pinAttempt,
                        onValueChange = {
                            pinAttempt = it
                            pinError = false
                        },
                        label = { Text("پین مدیر") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_pin_input")
                    )
                    Button(
                        onClick = {
                            if (pinAttempt == config.adminPin.ifBlank { "1234" }) {
                                isUnlocked = true
                                pinError = false
                            } else {
                                pinError = true
                            }
                        },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("admin_unlock_button")
                    ) {
                        Text("باز کردن قفل")
                    }
                }
                if (pinError) {
                    Text("پین وارد شده نادرست است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                // QR & Provisioning string export
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("رمزنگاری با پین (AES-GCM)", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = encryptProvision,
                        onCheckedChange = { encryptProvision = it }
                    )
                }

                if (encryptProvision) {
                    OutlinedTextField(
                        value = provisionPin,
                        onValueChange = { provisionPin = it },
                        label = { Text("پین رمزگشایی فایل تنظیمات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "کد QR پیکربندی",
                            modifier = Modifier
                                .size(220.dp)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = provisionPayload,
                            style = MonoTextStyle.copy(fontSize = 11.sp),
                            maxLines = 3,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("IssabelGateProvision", provisionPayload))
                        Toast.makeText(context, "رشته ISSABEL-GATE کپی شد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("کپی رشته تنظیمات ISSABEL-GATE://")
                }

                // MikroTik Script Generator
                Spacer(modifier = Modifier.height(6.dp))
                Text("تولیدکننده خودکار اسکریپت MikroTik RouterOS", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouterOsVersion.entries.forEach { ver ->
                        FilterChip(
                            selected = rosVersion == ver,
                            onClick = { rosVersion = ver },
                            label = { Text(ver.label) }
                        )
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = mikrotikScript,
                            style = MonoTextStyle.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("MikroTikScript", mikrotikScript))
                            Toast.makeText(context, "اسکریپت میکروتیک کپی شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_mikrotik_script_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("کپی اسکریپت")
                    }

                    OutlinedButton(
                        onClick = onOpenSourcePanel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("راهنمای بومی سرویس")
                    }
                }
            }
        }
    }
}
