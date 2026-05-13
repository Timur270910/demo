package com.aiagent.client.data.security

import android.content.SharedPreferences
import android.util.Base64
import javax.inject.Inject
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorage @Inject constructor(
    private val encryptedSharedPreferences: SharedPreferences
) {
    companion object {
        private const val KEYSTORE_ALIAS = "ai_agent_keystore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        
        // Preference keys
        private const val KEY_SSH_HOST = "ssh_host"
        private const val KEY_SSH_PORT = "ssh_port"
        private const val KEY_SSH_USER = "ssh_user"
        private const val KEY_SSH_PASSWORD = "ssh_password"
        private const val KEY_AI_BASE_URL = "ai_base_url"
        private const val KEY_AI_API_KEY = "ai_api_key"
        private const val KEY_AI_MODEL = "ai_model"
    }

    private val cipher: Cipher = Cipher.getInstance(TRANSFORMATION)

    fun saveSshConfig(host: String, port: Int, username: String, password: String) {
        encryptedSharedPreferences.edit().apply {
            putString(KEY_SSH_HOST, encrypt(host))
            putInt(KEY_SSH_PORT, port)
            putString(KEY_SSH_USER, encrypt(username))
            putString(KEY_SSH_PASSWORD, encrypt(password))
            apply()
        }
    }

    fun getSshHost(): String? = decrypt(encryptedSharedPreferences.getString(KEY_SSH_HOST, null))
    fun getSshPort(): Int = encryptedSharedPreferences.getInt(KEY_SSH_PORT, 22)
    fun getSshUser(): String? = decrypt(encryptedSharedPreferences.getString(KEY_SSH_USER, null))
    fun getSshPassword(): String? = decrypt(encryptedSharedPreferences.getString(KEY_SSH_PASSWORD, null))

    fun saveAiConfig(baseUrl: String, apiKey: String, model: String) {
        encryptedSharedPreferences.edit().apply {
            putString(KEY_AI_BASE_URL, encrypt(baseUrl))
            putString(KEY_AI_API_KEY, encrypt(apiKey))
            putString(KEY_AI_MODEL, encrypt(model))
            apply()
        }
    }

    fun getAiBaseUrl(): String? = decrypt(encryptedSharedPreferences.getString(KEY_AI_BASE_URL, null))
    fun getAiApiKey(): String? = decrypt(encryptedSharedPreferences.getString(KEY_AI_API_KEY, null))
    fun getAiModel(): String? = decrypt(encryptedSharedPreferences.getString(KEY_AI_MODEL, null))

    private fun encrypt(data: String): String {
        val key = getOrCreateSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    private fun decrypt(data: String?): String? {
        if (data == null) return null
        val key = getOrCreateSecretKey()
        val combined = Base64.decode(data, Base64.DEFAULT)
        val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
        val encryptedBytes = combined.sliceArray(GCM_IV_LENGTH until combined.size)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val existingEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? java.security.KeyStore.SecretKeyEntry
        if (existingEntry != null) {
            return existingEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        keyGenerator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
