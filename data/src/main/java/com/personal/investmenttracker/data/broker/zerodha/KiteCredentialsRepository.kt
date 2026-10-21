package com.personal.investmenttracker.data.broker.zerodha

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class KiteCredentialsRepository(
    private val context: Context,
    private val prefsName: String = DEFAULT_PREFS_NAME,
    preferences: SharedPreferences? = null,
) : KiteSessionTokenProvider {
    private val masterKey: MasterKey by lazy {
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        preferences ?: EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveCredentials(
        apiKey: String,
        apiSecret: String,
    ) {
        encryptedPrefs
            .edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_API_SECRET, apiSecret.trim())
            .apply()
    }

    fun saveSession(
        accessToken: String,
        userId: String,
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        encryptedPrefs
            .edit()
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .putString(KEY_USER_ID, userId.trim())
            .putLong(KEY_SESSION_CREATED_AT, timestampMs)
            .apply()
    }

    override fun getApiKey(): String? = encryptedPrefs.getString(KEY_API_KEY, null)

    fun getApiSecret(): String? = encryptedPrefs.getString(KEY_API_SECRET, null)

    override fun getAccessToken(): String? = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getUserId(): String? = encryptedPrefs.getString(KEY_USER_ID, null)

    fun hasCredentials(): Boolean = !getApiKey().isNullOrBlank() && !getApiSecret().isNullOrBlank()

    fun isSessionValid(): Boolean {
        val token = getAccessToken()
        if (token.isNullOrBlank()) return false
        val createdAt = encryptedPrefs.getLong(KEY_SESSION_CREATED_AT, 0L)
        return !KiteAuthHelper.isSessionExpired(createdAt)
    }

    fun clearSession() {
        encryptedPrefs
            .edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_SESSION_CREATED_AT)
            .apply()
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_PREFS_NAME = "kite_encrypted_credentials"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SESSION_CREATED_AT = "session_created_at"
        private const val KEY_USER_ID = "user_id"
    }
}
