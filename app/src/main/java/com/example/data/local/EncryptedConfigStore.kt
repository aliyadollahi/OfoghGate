package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.model.SoftphoneConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class EncryptedConfigStore(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val prefs: SharedPreferences = createSecurePrefs(context.applicationContext)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<SoftphoneConfig> = _configFlow.asStateFlow()

    private fun createSecurePrefs(appContext: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            runCatching {
                appContext.deleteSharedPreferences(PREFS_NAME)
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrElse {
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    fun loadConfig(): SoftphoneConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return SoftphoneConfig()
        return runCatching { json.decodeFromString(SoftphoneConfig.serializer(), raw) }
            .getOrElse { SoftphoneConfig() }
    }

    fun saveConfig(config: SoftphoneConfig) {
        val sanitized = config.copy(
            knockSteps = config.knockSteps.take(6).ifEmpty { SoftphoneConfig().knockSteps },
            knockIntervalMinutes = config.knockIntervalMinutes.coerceIn(1, 180)
        )
        val encoded = json.encodeToString(SoftphoneConfig.serializer(), sanitized)
        prefs.edit().putString(KEY_CONFIG_JSON, encoded).apply()
        _configFlow.value = sanitized
    }

    fun getLastKnockEpochMs(): Long = prefs.getLong(KEY_LAST_KNOCK_MS, 0L)

    fun setLastKnockEpochMs(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_KNOCK_MS, epochMs).apply()
    }

    fun getNextKnockEpochMs(): Long = prefs.getLong(KEY_NEXT_KNOCK_MS, 0L)

    fun setNextKnockEpochMs(epochMs: Long) {
        prefs.edit().putLong(KEY_NEXT_KNOCK_MS, epochMs).apply()
    }

    companion object {
        private const val PREFS_NAME = "issabel_gate_secure_prefs"
        private const val KEY_CONFIG_JSON = "softphone_config_json"
        private const val KEY_LAST_KNOCK_MS = "last_knock_epoch_ms"
        private const val KEY_NEXT_KNOCK_MS = "next_knock_epoch_ms"
    }
}
