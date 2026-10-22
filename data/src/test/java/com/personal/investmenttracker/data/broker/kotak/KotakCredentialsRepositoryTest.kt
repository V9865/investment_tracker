package com.personal.investmenttracker.data.broker.kotak

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
class KotakCredentialsRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: KotakCredentialsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val testPrefs = context.getSharedPreferences("test_kotak_prefs", Context.MODE_PRIVATE)
        repository = KotakCredentialsRepository(context, "test_kotak_prefs", testPrefs)
        repository.clearCredentials()
    }

    @Test
    fun saveAndRetrieveCredentials() {
        assertFalse(repository.hasCredentials())

        repository.saveCredentials("access_token_123", "sid_456", "consumer_key_789")

        assertTrue(repository.hasCredentials())
        assertEquals("access_token_123", repository.getAccessToken())
        assertEquals("sid_456", repository.getSid())
        assertEquals("consumer_key_789", repository.getConsumerKey())
    }

    @Test
    fun clearCredentials_removesAllTokens() {
        repository.saveCredentials("access_token_123", "sid_456", "consumer_key_789")
        assertTrue(repository.hasCredentials())

        repository.clearCredentials()
        assertFalse(repository.hasCredentials())
        assertNull(repository.getAccessToken())
        assertNull(repository.getSid())
        assertNull(repository.getConsumerKey())
    }
}
