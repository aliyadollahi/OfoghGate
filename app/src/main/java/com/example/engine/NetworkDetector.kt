package com.example.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.example.data.model.LogCategory
import com.example.data.model.SoftphoneConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address

data class NetworkEnvState(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val transportLabel: String = "قطع",
    val localIp: String = "-",
    val wifiSsid: String = "",
    val networkSignature: String = ""
)

class NetworkDetector(
    private val context: Context,
    private val portKnocker: PortKnocker,
    private val logger: DiagnosticLogger
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _envState = MutableStateFlow(readCurrentEnvironment())
    val envState: StateFlow<NetworkEnvState> = _envState.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var onNetworkChangedListener: ((NetworkEnvState) -> Unit)? = null
    private var lastSignature: String = ""

    fun startMonitoring(onChanged: (NetworkEnvState) -> Unit) {
        onNetworkChangedListener = onChanged
        if (callback != null) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handleChange("اتصال شبکه برقرار شد")
            }

            override fun onLost(network: Network) {
                handleChange("اتصال شبکه قطع شد")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                handleChange("تغییر نوع شبکه")
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                handleChange("تغییر IP یا مسیر شبکه")
            }
        }
        callback = cb
        runCatching {
            connectivityManager.registerNetworkCallback(request, cb)
        }.onFailure {
            logger.log(LogCategory.NETWORK, "خطا در ثبت NetworkCallback", it.message ?: "", true)
        }
        handleChange("بررسی اولیه شبکه")
    }

    fun stopMonitoring() {
        callback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        callback = null
        onNetworkChangedListener = null
    }

    private fun handleChange(reason: String) {
        val current = readCurrentEnvironment()
        _envState.value = current
        if (current.networkSignature != lastSignature) {
            lastSignature = current.networkSignature
            logger.log(
                LogCategory.NETWORK,
                "$reason: ${current.transportLabel}",
                "IP: ${current.localIp}" + if (current.wifiSsid.isNotBlank()) " | SSID: ${current.wifiSsid}" else ""
            )
            onNetworkChangedListener?.invoke(current)
        }
    }

    fun readCurrentEnvironment(): NetworkEnvState {
        val activeNetwork = connectivityManager.activeNetwork
            ?: return NetworkEnvState(isConnected = false, transportLabel = "بدون شبکه")
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkEnvState(isConnected = false, transportLabel = "بدون دسترسی")
        val linkProps = connectivityManager.getLinkProperties(activeNetwork)

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        val localIp = linkProps?.linkAddresses
            ?.mapNotNull { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
            ?: "-"

        val label = when {
            isVpn && isWifi -> "Wi-Fi + VPN"
            isVpn && isCellular -> "دیتا موبایل + VPN"
            isWifi -> "Wi-Fi"
            isCellular -> "دیتا موبایل (4G/5G)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "سایر"
        }

        val ssid = if (isWifi) readWifiSsidSafely() else ""
        val signature = "$hasInternet|$isWifi|$isCellular|$isVpn|$localIp|$ssid"

        return NetworkEnvState(
            isConnected = hasInternet,
            isWifi = isWifi,
            isCellular = isCellular,
            transportLabel = label,
            localIp = localIp,
            wifiSsid = ssid,
            networkSignature = signature
        )
    }

    @Suppress("DEPRECATION")
    private fun readWifiSsidSafely(): String {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) return ""
        return runCatching {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val raw = wifiManager.connectionInfo?.ssid.orEmpty()
            if (raw == "<unknown ssid>") "" else raw.trim('"')
        }.getOrDefault("")
    }

    suspend fun isInsideOfficeLan(config: SoftphoneConfig): Boolean {
        val env = readCurrentEnvironment()
        if (!env.isConnected) return false
        // Cellular without VPN is outside LAN
        if (env.isCellular && !env.transportLabel.contains("VPN")) {
            return false
        }
        val configuredSsid = config.officeWifiSsid.trim()
        if (configuredSsid.isNotBlank() && env.wifiSsid.isNotBlank()) {
            val ssidMatch = env.wifiSsid.equals(configuredSsid, ignoreCase = true)
            if (!ssidMatch) {
                logger.log(
                    LogCategory.NETWORK,
                    "نام Wi-Fi با شبکه سازمان مطابقت ندارد (${env.wifiSsid})",
                    "تشخیص مسیر: خارج از شبکه (WAN)"
                )
                return false
            }
        }

        val lanHost = config.lanHost.trim()
        if (lanHost.isBlank()) return false

        val probe = portKnocker.probeHost(lanHost, config.lanPort, timeoutMs = 850)
        logger.log(
            LogCategory.NETWORK,
            if (probe.reachable) "سرور داخلی ایزابل در دسترس است (LAN)" else "سرور داخلی پاسخ نداد (مسیر WAN)",
            "$lanHost:${config.lanPort} -> ${probe.detail}"
        )
        return probe.reachable
    }
}
