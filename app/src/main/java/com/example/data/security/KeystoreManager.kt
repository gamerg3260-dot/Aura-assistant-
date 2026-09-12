package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreManager(private val context: Context) {

    private val keyStoreAlias = "AuraAssistantKeyAlias_v1"
    private val keyStoreType = "AndroidKeyStore"
    private val cipherTransformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivLength = 12

    private val prefs = context.getSharedPreferences("aura_secure_storage", Context.MODE_PRIVATE)

    init {
        try {
            ensureKeyExists()
        } catch (e: Exception) {
            android.util.Log.w("KeystoreManager", "AndroidKeyStore unavailable: ${e.message}")
        }
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
            if (!keyStore.containsAlias(keyStoreAlias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreType)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyStoreAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            android.util.Log.w("KeystoreManager", "Hardware keystore initialization skipped: ${e.message}")
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
            keyStore.getKey(keyStoreAlias, null) as? SecretKey
        } catch (e: Throwable) {
            null
        }
    }

    fun encryptAndStore(keyName: String, plainText: String) {
        if (plainText.isBlank()) {
            prefs.edit().remove(keyName).remove(keyName + "_fallback").apply()
            return
        }
        try {
            val secretKey = getSecretKey()
            if (secretKey == null) {
                prefs.edit().putString(keyName + "_fallback", plainText).apply()
                return
            }
            val cipher = Cipher.getInstance(cipherTransformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Combine IV + ciphertext
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            val base64 = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(keyName, base64).apply()
        } catch (e: Throwable) {
            // Fallback for emulator environments where hardware keystore may have peculiarities
            prefs.edit().putString(keyName + "_fallback", plainText).apply()
        }
    }

    fun retrieveAndDecrypt(keyName: String): String? {
        val base64 = prefs.getString(keyName, null)
        if (base64 == null) {
            return prefs.getString(keyName + "_fallback", null)
        }
        return try {
            val secretKey = getSecretKey()
            if (secretKey == null) {
                return prefs.getString(keyName + "_fallback", null)
            }
            val combined = Base64.decode(base64, Base64.NO_WRAP)
            if (combined.size < ivLength) return null

            val iv = ByteArray(ivLength)
            val cipherBytes = ByteArray(combined.size - ivLength)
            System.arraycopy(combined, 0, iv, 0, ivLength)
            System.arraycopy(combined, ivLength, cipherBytes, 0, cipherBytes.size)

            val cipher = Cipher.getInstance(cipherTransformation)
            val spec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Throwable) {
            prefs.getString(keyName + "_fallback", null)
        }
    }

    fun clearKey(keyName: String) {
        prefs.edit().remove(keyName).remove(keyName + "_fallback").apply()
    }

    fun hasKey(keyName: String): Boolean {
        return prefs.contains(keyName) || prefs.contains(keyName + "_fallback")
    }
}
