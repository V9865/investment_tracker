package com.personal.investmenttracker.data.broker.kotak

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class KotakCredentialsRepository(
    private val context: Context,
    private val prefsName: String = DEFAULT_PREFS_NAME,
    preferences: SharedPreferences? = null,
) {
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
        accessToken: String,
        sid: String? = null,
        consumerKey: String? = null,
    ) {
        encryptedPrefs
            .edit()
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .putString(KEY_SID, sid?.trim())
            .putString(KEY_CONSUMER_KEY, consumerKey?.trim())
            .apply()
    }

    fun getAccessToken(): String? = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getSid(): String? = encryptedPrefs.getString(KEY_SID, null)

    fun getConsumerKey(): String? = encryptedPrefs.getString(KEY_CONSUMER_KEY, null)

    fun hasCredentials(): Boolean = !getAccessToken().isNullOrBlank()

    fun clearCredentials() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_PREFS_NAME = "kotak_encrypted_credentials"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SID = "sid"
        private const val KEY_CONSUMER_KEY = "consumer_key"
    }
}
