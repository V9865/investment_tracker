package com.personal.investmenttracker.data.broker.kotak

import com.personal.investmenttracker.domain.model.Broker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal

class MockKotakProviderTest {
    private lateinit var provider: MockKotakProvider

    @Before
    fun setUp() {
        provider = MockKotakProvider()
    }

    @Test
    fun defaultSuccessMode_returnsSection5WorkedExampleHoldings() =
        runTest {
            val result = provider.fetchHoldings()
            assertTrue(result.isSuccess)

            val holdings = result.getOrThrow()
            assertEquals(3, holdings.size)

            // Worked example 4: SBIN 5 @ 110, price 120
            val sbin = holdings.first { it.first.symbol == "SBIN" }.first
            val sbinQuote = holdings.first { it.first.symbol == "SBIN" }.second

            assertEquals(Broker.KOTAK_NEO, sbin.broker)
            assertEquals("INE062A01020", sbin.isin)
            assertEquals(BigDecimal.valueOf(5), sbin.quantity.value)
            assertEquals("110.00", sbin.avgPriceReported!!.amount.toPlainString())
            assertEquals("120.00", sbinQuote.price.amount.toPlainString())

            assertFalse(provider.isSessionExpired())
            assertTrue(provider.hasCredentials())
            assertFalse(provider.getSessionBannerState().isVisible)
        }

    @Test
    fun expiredMode_failsWithKotakSessionExpiredException_andShowsBanner() =
        runTest {
            provider.mode = MockKotakMode.EXPIRED

            val result = provider.fetchHoldings()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KotakSessionExpiredException)
            assertTrue(provider.isSessionExpired())

            val banner = provider.getSessionBannerState()
            assertTrue(banner.isVisible)
            assertTrue(banner.message.contains("expired"))
        }

    @Test
    fun importHoldingsFallback_worksCorrectly() {
        val csv =
            """
            ISIN,Symbol,Exchange,Quantity,Average Price,LTP,Close Price
            INE062A01020,SBIN,BSE,5,110.00,120.00,120.00
            """.trimIndent()

        val result = provider.importHoldings(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals("SBIN", list[0].first.symbol)
        assertEquals(BigDecimal.valueOf(5), list[0].first.quantity.value)
    }
}
