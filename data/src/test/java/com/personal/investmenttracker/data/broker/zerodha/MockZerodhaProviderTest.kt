package com.personal.investmenttracker.data.broker.zerodha

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MockZerodhaProviderTest {
    private lateinit var provider: MockZerodhaProvider

    @Before
    fun setUp() {
        provider = MockZerodhaProvider()
    }

    @Test
    fun defaultSuccessMode_returnsIllustrativeHoldings() =
        runTest {
            val result = provider.fetchHoldings()
            assertTrue(result.isSuccess)

            val holdings = result.getOrNull()
            assertNotNull(holdings)
            assertEquals(5, holdings!!.size)

            val symbols = holdings.map { it.first.symbol }
            assertTrue(symbols.contains("INFY"))
            assertTrue(symbols.contains("TCS"))
            assertTrue(symbols.contains("RELIANCE"))
            assertTrue(symbols.contains("SBIN"))
            assertTrue(symbols.contains("TATAMOTORS"))

            assertFalse(provider.isSessionExpired())
            assertFalse(provider.getSessionBannerState().isVisible)
        }

    @Test
    fun expiredMode_failsWithSessionExpiredExceptionAndShowsBanner() =
        runTest {
            provider.mode = MockProviderMode.EXPIRED_403

            val result = provider.fetchHoldings()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KiteSessionExpiredException)

            assertTrue(provider.isSessionExpired())
            val banner = provider.getSessionBannerState()
            assertTrue(banner.isVisible)
            assertTrue(banner.message.contains("session expired"))
            assertNotNull(banner.loginUrl)
        }

    @Test
    fun rateLimitMode_failsWithRateLimitException() =
        runTest {
            provider.mode = MockProviderMode.RATE_LIMIT_429

            val result = provider.fetchHoldings()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KiteRateLimitException)
        }

    @Test
    fun timeoutMode_failsWithTimeoutException() =
        runTest {
            provider.mode = MockProviderMode.TIMEOUT

            val result = provider.fetchHoldings()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KiteNetworkTimeoutException)
        }

    @Test
    fun exchangeToken_recoversFromExpiredState() =
        runTest {
            provider.mode = MockProviderMode.EXPIRED_403
            assertTrue(provider.isSessionExpired())

            val exchangeResult = provider.exchangeToken("valid_request_token_12345")
            assertTrue(exchangeResult.isSuccess)
            assertFalse(provider.isSessionExpired())
            assertFalse(provider.getSessionBannerState().isVisible)

            val holdingsResult = provider.fetchHoldings()
            assertTrue(holdingsResult.isSuccess)
        }
}
