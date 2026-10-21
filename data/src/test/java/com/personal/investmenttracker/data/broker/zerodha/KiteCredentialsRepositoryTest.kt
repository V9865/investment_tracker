package com.personal.investmenttracker.data.broker.zerodha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class KiteCredentialsRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: KiteCredentialsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val testPrefs = context.getSharedPreferences("test_kite_prefs", Context.MODE_PRIVATE)
        repository = KiteCredentialsRepository(context, "test_kite_prefs", testPrefs)
        repository.clearAll()
    }

    @Test
    fun saveAndRetrieveCredentials() {
        assertFalse(repository.hasCredentials())

        repository.saveCredentials("key_123", "secret_456")

        assertTrue(repository.hasCredentials())
        assertEquals("key_123", repository.getApiKey())
        assertEquals("secret_456", repository.getApiSecret())
    }

    @Test
    fun saveAndValidateSession() {
        assertFalse(repository.isSessionValid())

        repository.saveSession("access_token_abc", "user_001")

        assertTrue(repository.isSessionValid())
        assertEquals("access_token_abc", repository.getAccessToken())
        assertEquals("user_001", repository.getUserId())

        repository.clearSession()
        assertFalse(repository.isSessionValid())
        assertNull(repository.getAccessToken())
    }
}
