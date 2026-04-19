package com.jatm.androidphoneapi.apikey

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun interface ApiKeyGenerator {
    fun generate(): String
}

class SecureRandomApiKeyGenerator(
    private val secureRandom: SecureRandom = strongSecureRandom(),
) : ApiKeyGenerator {
    override fun generate(): String {
        val bytes = ByteArray(KEY_BYTES)
        secureRandom.nextBytes(bytes)
        return API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val KEY_BYTES = 32
        const val API_KEY_PREFIX = "apa_live_"
    }
}

fun interface SaltGenerator {
    fun generateSalt(): ByteArray
}

class SecureRandomSaltGenerator(
    private val secureRandom: SecureRandom = strongSecureRandom(),
) : SaltGenerator {
    override fun generateSalt(): ByteArray =
        ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }

    private companion object {
        const val SALT_BYTES = 16
    }
}

object ApiKeyHasher {
    fun hashSha256(apiKey: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(apiKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    fun matches(apiKey: String, salt: ByteArray, expectedHashHex: String): Boolean {
        val candidate = hashSha256(apiKey, salt)
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            expectedHashHex.toByteArray(Charsets.UTF_8),
        )
    }
}

interface SecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(encodedCiphertext: String): String
}

class AndroidKeystoreAesGcmCipher(
    private val keyAlias: String = "android_phone_api_key_cipher",
) : SecretCipher {
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(encodedCiphertext: String): String {
        val combined = Base64.getDecoder().decode(encodedCiphertext)
        require(combined.size > IV_BYTES) { "Invalid encrypted API key" }
        val iv = combined.copyOfRange(0, IV_BYTES)
        val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

private fun strongSecureRandom(): SecureRandom =
    runCatching { SecureRandom.getInstanceStrong() }.getOrElse { SecureRandom() }
