package com.personal.investmenttracker.data.broker.zerodha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class KiteAuthHelperTest {
    private val istZone = ZoneId.of("Asia/Kolkata")

    @Test
    fun computeChecksum_producesCorrectSha256Hex() {
        val apiKey = "test_api_key"
        val requestToken = "test_request_token"
        val apiSecret = "test_api_secret"

        val checksum = KiteAuthHelper.computeChecksum(apiKey, requestToken, apiSecret)

        // Precalculated sha256("test_api_keytest_request_tokentest_api_secret")
        assertNotNull(checksum)
        assertEquals(64, checksum.length)
        assertEquals("db7982386217016217ea3d380e90d51dc63978441969b4f101ec3302f80bd06d", checksum)
    }

    @Test
    fun buildLoginUrl_formatsCorrectQueryParameters() {
        val url = KiteAuthHelper.buildLoginUrl("my_key")
        assertEquals("https://kite.zerodha.com/connect/login?v=3&api_key=my_key", url)

        val urlWithParams = KiteAuthHelper.buildLoginUrl("my_key", "step=portfolio")
        assertEquals(
            "https://kite.zerodha.com/connect/login?v=3&api_key=my_key&redirect_params=step=portfolio",
            urlWithParams,
        )
    }

    @Test
    fun extractRequestToken_parsesFromFullUrlAndRawString() {
        val url1 = "https://127.0.0.1:8080/callback?action=login&status=success&request_token=abc123token456"
        assertEquals("abc123token456", KiteAuthHelper.extractRequestToken(url1))

        val customScheme = "investmenttracker://kite-redirect?request_token=xyz789token000&status=success"
        assertEquals("xyz789token000", KiteAuthHelper.extractRequestToken(customScheme))

        val rawToken = "rawtoken1234567890abcdef"
        assertEquals("rawtoken1234567890abcdef", KiteAuthHelper.extractRequestToken(rawToken))

        val invalidUrl = "https://example.com/callback?error=access_denied"
        assertNull(KiteAuthHelper.extractRequestToken(invalidUrl))

        val tooShort = "abc"
        assertNull(KiteAuthHelper.extractRequestToken(tooShort))

        val invalidChars = "invalid token with spaces"
        assertNull(KiteAuthHelper.extractRequestToken(invalidChars))
    }

    @Test
    fun isSessionExpired_respects6AmIstDailyCutoff() {
        // Base day: 2026-10-22
        val baseDate = ZonedDateTime.of(2026, 10, 22, 10, 0, 0, 0, istZone) // 10:00 AM IST on Oct 22
        val sessionCreatedMs = baseDate.toInstant().toEpochMilli()

        // Same day 15:30 IST (market close) -> still valid
        val sameDayAfternoonMs = baseDate.with(LocalTime.of(15, 30)).toInstant().toEpochMilli()
        assertFalse(KiteAuthHelper.isSessionExpired(sessionCreatedMs, sameDayAfternoonMs))

        // Same day 23:59 IST -> still valid
        val sameDayNightMs = baseDate.with(LocalTime.of(23, 59)).toInstant().toEpochMilli()
        assertFalse(KiteAuthHelper.isSessionExpired(sessionCreatedMs, sameDayNightMs))

        // Next day 05:59 AM IST -> still valid
        val nextDay559AmMs =
            baseDate
                .plusDays(1)
                .with(LocalTime.of(5, 59))
                .toInstant()
                .toEpochMilli()
        assertFalse(KiteAuthHelper.isSessionExpired(sessionCreatedMs, nextDay559AmMs))

        // Next day 06:00 AM IST -> EXPIRED
        val nextDay600AmMs =
            baseDate
                .plusDays(1)
                .with(LocalTime.of(6, 0))
                .toInstant()
                .toEpochMilli()
        assertTrue(KiteAuthHelper.isSessionExpired(sessionCreatedMs, nextDay600AmMs))

        // Next day 07:00 AM IST -> EXPIRED
        val nextDay700AmMs =
            baseDate
                .plusDays(1)
                .with(LocalTime.of(7, 0))
                .toInstant()
                .toEpochMilli()
        assertTrue(KiteAuthHelper.isSessionExpired(sessionCreatedMs, nextDay700AmMs))
    }
}
