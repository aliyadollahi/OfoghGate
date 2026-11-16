package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.ActiveCallState
import com.example.engine.SoftphoneForegroundService
import com.example.ui.SoftphoneViewModel
import com.example.ui.call.IncomingCallActivity
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.NetworkKnockScreen
import com.example.ui.screens.SettingsAdminScreen
import com.example.ui.theme.IssabelGateTheme
import com.example.ui.theme.SignalEmerald
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Dialer : AppRoute
    @Serializable data object NetworkKnock : AppRoute
    @Serializable data object Diagnostics : AppRoute
    @Serializable data object SettingsAdmin : AppRoute
}

private data class BottomTabItem(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IssabelGateTheme {
                IssabelGateRootApp()
            }
        }
    }
}

@Composable
private fun IssabelGateRootApp(
    viewModel: SoftphoneViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val config by viewModel.config.collectAsStateWithLifecycle()
    val snapshot by viewModel.networkSnapshot.collectAsStateWithLifecycle()
    val callUiState by viewModel.callUiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val recentCalls by viewModel.recentCalls.collectAsStateWithLifecycle()
    val quickExtensions by viewModel.quickExtensions.collectAsStateWithLifecycle()
    val dialDigits by viewModel.dialDigits.collectAsStateWithLifecycle()
    val probeSummary by viewModel.probeSummary.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    var missingPermissions by remember {
        mutableStateOf(
            requiredPermissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        missingPermissions = requiredPermissions.filter { perm ->
            results[perm] != true &&
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        viewModel.startServiceIfConfigured()
    }

    LaunchedEffect(Unit) {
        if (missingPermissions.isEmpty()) {
            viewModel.startServiceIfConfigured()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dismissBanner()
    }

    val tabs = remember {
        listOf(
            BottomTabItem(AppRoute.Dialer, "شمارهگیر", Icons.Filled.Dialpad, Icons.Outlined.Dialpad, "tab_dialer"),
            BottomTabItem(AppRoute.NetworkKnock, "شبکه و ناک", Icons.Filled.Router, Icons.Outlined.Router, "tab_network"),
            BottomTabItem(AppRoute.Diagnostics, "لاگ", Icons.Filled.BugReport, Icons.Outlined.BugReport, "tab_diagnostics"),
            BottomTabItem(AppRoute.SettingsAdmin, "تنظیمات", Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hasRoute(tab.route::class) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, maxLines = 1) },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = callUiState.state != ActiveCallState.IDLE &&
                    callUiState.state != ActiveCallState.ENDED
            ) {
                Surface(
                    onClick = {
                        val intent = Intent(context, IncomingCallActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                        context.startActivity(intent)
                    },
                    color = SignalEmerald,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تماس فعال: ${callUiState.remoteNumber} (برای بازگشت لمس کنید)",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = missingPermissions.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "برای تماس صوتی و نمایش اعلان ورودی، مجوز میکروفون و اعلان لازم است.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { permissionLauncher.launch(missingPermissions.toTypedArray()) }
                        ) {
                            Text("تایید مجوزها")
                        }
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = AppRoute.Dialer,
                modifier = Modifier.weight(1f)
            ) {
                composable<AppRoute.Dialer> {
                    DialerScreen(
                        config = config,
                        snapshot = snapshot,
                        dialDigits = dialDigits,
                        quickExtensions = quickExtensions,
                        recentCalls = recentCalls,
                        onDigitPress = viewModel::appendDialDigit,
                        onBackspace = viewModel::backspaceDialDigit,
                        onClearDigits = viewModel::clearDialDigits,
                        onCallClick = { number -> viewModel.placeCall(number) },
                        onRefreshRegister = { SoftphoneForegroundService.startOrUpdate(context) },
                        onAddQuickExtension = viewModel::addQuickExtension,
                        onDeleteQuickExtension = viewModel::deleteQuickExtension,
                        onClearCallHistory = viewModel::clearCallHistory
                    )
                }

                composable<AppRoute.NetworkKnock> {
                    NetworkKnockScreen(
                        config = config,
                        snapshot = snapshot,
                        probeSummary = probeSummary,
                        onSelectNetworkMode = viewModel::switchNetworkMode,
                        onKnockNow = viewModel::triggerImmediateKnock,
                        onRunProbes = viewModel::runHostProbes
                    )
                }

                composable<AppRoute.Diagnostics> {
                    DiagnosticsScreen(
                        logs = logs,
                        isBatteryOptimizedIgnored = viewModel.isBatteryOptimizationIgnored(),
                        canScheduleExactAlarms = viewModel.canScheduleExactAlarms(),
                        onRequestBatteryExemption = {
                            runCatching { context.startActivity(viewModel.buildBatteryOptimizationIntent()) }
                        },
                        onRequestExactAlarms = {
                            viewModel.buildExactAlarmSettingsIntent()?.let {
                                runCatching { context.startActivity(it) }
                            }
                        },
                        onRequestFullScreenIntent = {
                            viewModel.buildFullScreenIntentSettings()?.let {
                                runCatching { context.startActivity(it) }
                            }
                        },
                        onClearLogs = viewModel::clearDiagnosticLogs
                    )
                }

                composable<AppRoute.SettingsAdmin> {
                    SettingsAdminScreen(
                        config = config,
                        onSaveConfig = viewModel::saveConfig,
                        onDecodeProvision = viewModel::decodeProvisionPayload,
                        onEncodeProvision = viewModel::encodeProvisionPayload,
                        onBuildMikroTikScript = viewModel::buildMikroTikScript
                    )
                }
            }
        }
    }
}
