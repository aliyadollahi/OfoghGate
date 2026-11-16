package com.example.data.provisioning

import com.example.data.model.SoftphoneConfig
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed interface ProvisionDecodeResult {
    data class Success(val config: SoftphoneConfig) : ProvisionDecodeResult
    data object PinRequired : ProvisionDecodeResult
    data object WrongPinOrCorrupted : ProvisionDecodeResult
    data object InvalidFormat : ProvisionDecodeResult
}

object ProvisioningCodec {

    private const val PREFIX_PLAIN = "ISSABEL-GATE://v1.plain."
    private const val PREFIX_ENC = "ISSABEL-GATE://v1.enc."
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_BITS = 128

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodePlain(config: SoftphoneConfig): String {
        val rawJson = json.encodeToString(SoftphoneConfig.serializer(), config)
        val b64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rawJson.toByteArray(StandardCharsets.UTF_8))
        return "$PREFIX_PLAIN$b64"
    }

    fun encodeEncrypted(config: SoftphoneConfig, pin: String): String {
        require(pin.isNotBlank()) { "PIN cannot be blank" }
        val rawJson = json.encodeToString(SoftphoneConfig.serializer(), config)
            .toByteArray(StandardCharsets.UTF_8)

        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        val key = deriveAesKey(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(rawJson)

        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "$PREFIX_ENC${encoder.encodeToString(salt)}.${encoder.encodeToString(iv)}.${encoder.encodeToString(encrypted)}"
    }

    fun isEncryptedPayload(payload: String): Boolean {
        return payload.trim().startsWith(PREFIX_ENC)
    }

    fun decode(payload: String, pin: String? = null): ProvisionDecodeResult {
        val trimmed = payload.trim()
        return when {
            trimmed.startsWith(PREFIX_PLAIN) -> {
                val b64 = trimmed.removePrefix(PREFIX_PLAIN)
                runCatching {
                    val decoded = Base64.getUrlDecoder().decode(b64)
                    val cfg = json.decodeFromString(
                        SoftphoneConfig.serializer(),
                        String(decoded, StandardCharsets.UTF_8)
                    )
                    ProvisionDecodeResult.Success(cfg)
                }.getOrElse { ProvisionDecodeResult.InvalidFormat }
            }

            trimmed.startsWith(PREFIX_ENC) -> {
                if (pin.isNullOrBlank()) {
                    return ProvisionDecodeResult.PinRequired
                }
                val parts = trimmed.removePrefix(PREFIX_ENC).split(".")
                if (parts.size != 3) return ProvisionDecodeResult.InvalidFormat

                runCatching {
                    val decoder = Base64.getUrlDecoder()
                    val salt = decoder.decode(parts[0])
                    val iv = decoder.decode(parts[1])
                    val cipherBytes = decoder.decode(parts[2])

                    val key = deriveAesKey(pin, salt)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    val plainBytes = cipher.doFinal(cipherBytes)

                    val cfg = json.decodeFromString(
                        SoftphoneConfig.serializer(),
                        String(plainBytes, StandardCharsets.UTF_8)
                    )
                    ProvisionDecodeResult.Success(cfg)
                }.getOrElse { ProvisionDecodeResult.WrongPinOrCorrupted }
            }

            else -> ProvisionDecodeResult.InvalidFormat
        }
    }

    private fun deriveAesKey(pin: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val secretBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(secretBytes, "AES")
    }
}
