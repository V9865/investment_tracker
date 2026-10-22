package com.personal.investmenttracker.data.broker.kotak

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LiveKotakProviderTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context
    private lateinit var credentialsRepo: KotakCredentialsRepository
    private lateinit var provider: LiveKotakProvider

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        context = ApplicationProvider.getApplicationContext()
        val testPrefs = context.getSharedPreferences("test_live_kotak_prefs", Context.MODE_PRIVATE)
        credentialsRepo = KotakCredentialsRepository(context, "test_live_kotak_prefs", testPrefs)
        credentialsRepo.clearCredentials()

        val apiService =
            KotakNetworkClientFactory.createApiService(
                baseUrl = mockWebServer.url("/").toString(),
            )

        provider = LiveKotakProvider(apiService, credentialsRepo)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        credentialsRepo.clearCredentials()
    }

    @Test
    fun fetchHoldings_withoutCredentials_failsWithIllegalStateException() =
        runTest {
            val result = provider.fetchHoldings()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertTrue(provider.getSessionBannerState().isVisible)
        }

    @Test
    fun fetchHoldings_withValidToken_returnsDomainHoldings() =
        runTest {
            credentialsRepo.saveCredentials(
                accessToken = "mock_access_token_from_web",
                sid = "mock_sid_value",
                consumerKey = "mock_consumer_key",
            )

            val holdingsJson =
                """
                {
                  "stat": "Ok",
                  "data": [
                    {
                      "symbol": "SBIN",
                      "isin": "INE062A01020",
                      "exchangeSegment": "bse_cm",
                      "holdingQuantity": "5",
                      "realisedQuantity": "0",
                      "averagePrice": "110.00",
                      "ltp": "120.00",
                      "closePrice": "120.00"
                    }
                  ]
                }
                """.trimIndent()
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(holdingsJson))

            val result = provider.fetchHoldings()
            assertTrue(result.isSuccess)

            val holdings = result.getOrThrow()
            assertEquals(1, holdings.size)
            val (holding, quote) = holdings[0]
            assertEquals("SBIN", holding.symbol)
            assertEquals(BigDecimal("5"), holding.quantity.value)
            assertEquals("110.00", holding.avgPriceReported!!.amount.toPlainString())
            assertEquals("120.00", quote.price.amount.toPlainString())

            val recorded = mockWebServer.takeRequest()
            assertEquals("/portfolio/holdings", recorded.path)
            assertEquals("Bearer mock_access_token_from_web", recorded.getHeader("Authorization"))
            assertEquals("mock_sid_value", recorded.getHeader("Sid"))
            assertFalse(provider.isSessionExpired())
        }

    @Test
    fun fetchHoldings_whenTokenExpired_failsWithSessionExpiredException() =
        runTest {
            credentialsRepo.saveCredentials(accessToken = "expired_token")
            mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Token expired"}"""))

            val result = provider.fetchHoldings()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KotakSessionExpiredException)
            assertTrue(provider.isSessionExpired())
            assertTrue(provider.getSessionBannerState().isVisible)
        }

    @Test
    fun importHoldingsFallback_worksCorrectly() {
        val csv =
            """
            ISIN,Symbol,Exchange,Quantity,Average Price,LTP,Close Price
            INE062A01020,SBIN,BSE,5,110.00,120.00,120.00
            """.trimIndent()
        val importResult = provider.importHoldings(ByteArrayInputStream(csv.toByteArray()))
        assertTrue(importResult.isSuccess)
        val items = importResult.getOrThrow()
        assertEquals(1, items.size)
        assertEquals("SBIN", items[0].first.symbol)
        assertEquals(BigDecimal("5"), items[0].first.quantity.value)
    }
}
