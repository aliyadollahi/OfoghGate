package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.MonoTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidSourcePanelDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxSize(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "پنل فنی بومی (AndroidSourcePanel)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("source_panel_close")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Foreground Service") },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Heads-Up & Overlay") },
                        icon = { Icon(Icons.Default.PhoneInTalk, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedTab == 0) {
                        ForegroundServiceGuideSection()
                    } else {
                        IncomingCallOverlayGuideSection()
                    }
                }
            }
        }
    }
}

@Composable
private fun ForegroundServiceGuideSection() {
    Text(
        text = "معماری Foreground Service با نوتیفیکیشن پایدار (حفظ سوکت و رجیستر SIP)",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "برای جلوگیری از بسته شدن سوکت SIP و تایمر ناکینگ پس از کمینه شدن برنامه یا قفل شدن صفحه در اندروید ۸ تا ۱۶ و حالت Doze، سرویس باید با START_STICKY، کانال IMPORTANCE_HIGH (بدون صدا و ویبره مزاحم) و foregroundServiceType=\"phoneCall|dataSync\" اجرا شود و Keep-Alive دورهای روی هسته Linphone فعال باشد.",
        style = MaterialTheme.typography.bodyMedium
    )

    CodeBoilerplateCard(
        title = "1. AndroidManifest.xml — Service & Permissions",
        code = """
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service
    android:name=".engine.SoftphoneForegroundService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="phoneCall|dataSync" />
        """.trimIndent()
    )

    CodeBoilerplateCard(
        title = "2. SoftphoneForegroundService.kt — IMPORTANCE_HIGH Persistent Notification & SIP Loop",
        code = """
class SoftphoneForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_SERVICE, "سرویس پایدار سافتفون", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notif = NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ایزابل گیت")
            .setContentText("متصل به سرور SIP — آماده دریافت تماس")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1001, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1001, notif)
        }
        // Enable Linphone auto-iterate & SIP Keep-Alive packets
        core.isAutoIterateEnabled = true
        core.setKeepAliveEnabled(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
        """.trimIndent()
    )
}

@Composable
private fun IncomingCallOverlayGuideSection() {
    Text(
        text = "نمایش تمامصفحه تماس ورودی (Full-Screen CallStyle & SYSTEM_ALERT_WINDOW)",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "در اندروید ۱۰ به بالا، نمایش مستقیم Activity از پسزمینه محدود شده است. استاندارد رسمی گوگلپلی برای اپلیکیشنهای VoIP استفاده از USE_FULL_SCREEN_INTENT همراه با Notification.CallStyle.forIncomingCall و setShowWhenLocked(true) است. در رامهای سفارشی (مانند MIUI/HyperOS شیائومی) میتوان از مجوز SYSTEM_ALERT_WINDOW (نمایش روی برنامههای دیگر) جهت تضمین بالا آمدن صفحه تماس روی همه پنجرهها نیز بهره برد.",
        style = MaterialTheme.typography.bodyMedium
    )

    CodeBoilerplateCard(
        title = "1. بررسی مجوز SYSTEM_ALERT_WINDOW و هدایت به تنظیمات",
        code = """
// Manifest (در صورت نیاز برای دستگاههای خاص / توزیع سازمانی):
// <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
// استاندارد رسمی گوگلپلی برای تماس ورودی:
// <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />

fun ensureOverlayPermission(context: Context) {
    if (!Settings.canDrawOverlays(context)) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
        """.trimIndent()
    )

    CodeBoilerplateCard(
        title = "2. Heads-Up CallStyle Notification + IncomingCallActivity",
        code = """
val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
val fullScreenPi = PendingIntent.getActivity(
    context, 5100, fullScreenIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

val caller = Person.Builder().setName(displayName).setImportant(true).build()
val callNotification = Notification.Builder(context, CHANNEL_CALLS)
    .setSmallIcon(R.mipmap.ic_launcher)
    .setCategory(Notification.CATEGORY_CALL)
    .setOngoing(true)
    .setFullScreenIntent(fullScreenPi, true) // Forces Activity on lock screen!
    .setStyle(Notification.CallStyle.forIncomingCall(caller, declinePi, answerPi))
    .build()

// Inside IncomingCallActivity.onCreate():
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
    setShowWhenLocked(true)
    setTurnScreenOn(true)
}
        """.trimIndent()
    )
}

@Composable
private fun CodeBoilerplateCard(
    title: String,
    code: String
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(title, code))
                        Toast.makeText(context, "کد کپی شد", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "کپی کد", modifier = Modifier.padding(end = 4.dp))
                    Text("کپی")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = code,
                        style = MonoTextStyle.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}
