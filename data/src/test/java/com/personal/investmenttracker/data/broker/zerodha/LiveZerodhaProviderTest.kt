package com.personal.investmenttracker.data.broker.zerodha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LiveZerodhaProviderTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context
    private lateinit var credentialsRepo: KiteCredentialsRepository
    private lateinit var provider: LiveZerodhaProvider

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        context = ApplicationProvider.getApplicationContext()
        val testPrefs = context.getSharedPreferences("test_live_zerodha_prefs", Context.MODE_PRIVATE)
        credentialsRepo = KiteCredentialsRepository(context, "test_live_zerodha_prefs", testPrefs)
        credentialsRepo.clearAll()

        val apiService =
            KiteNetworkClientFactory.createApiService(
                tokenProvider = credentialsRepo,
                baseUrl = mockWebServer.url("/").toString(),
                retryInterceptor =
                    KiteRetryInterceptor(
                        maxRetries = 1,
                        initialDelayMs = 10,
                        maxJitterMs = 0,
                        sleeper = {},
                    ),
            )

        provider = LiveZerodhaProvider(apiService, credentialsRepo)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        credentialsRepo.clearAll()
    }

    @Test
    fun unconfiguredCredentials_failsFastWithoutCallingNetwork() =
        runTest {
            val result = provider.fetchHoldings()
            assertTrue(result.isFailure)
            assertEquals(0, mockWebServer.requestCount)
            assertFalse(provider.getSessionBannerState().isVisible)
        }

    @Test
    fun expiredSession_failsFastWithSessionExpiredException() =
        runTest {
            credentialsRepo.saveCredentials("test_api_key", "test_api_secret")
            // Token is not set, so session is invalid/expired
            val result = provider.fetchHoldings()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KiteSessionExpiredException)
            assertEquals(0, mockWebServer.requestCount)
            assertTrue(provider.getSessionBannerState().isVisible)
        }

    @Test
    fun validSession_fetchesHoldingsAndMapsCorrectly() =
        runTest {
            credentialsRepo.saveCredentials("my_key", "my_secret")
            credentialsRepo.saveSession("my_token", "user_1")

            val holdingsJson =
                """
                {
                  "status": "success",
                  "data": [
                    {
                      "tradingsymbol": "INFY",
                      "exchange": "NSE",
                      "isin": "INE009A01021",
                      "quantity": 10,
                      "t1_quantity": 5,
                      "average_price": 1400.0,
                      "last_price": 1500.0,
                      "close_price": 1480.0
                    }
                  ]
                }
                """.trimIndent()
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(holdingsJson))

            val result = provider.fetchHoldings()
            assertTrue(result.isSuccess)

            val holdings = result.getOrNull()
            assertNotNull(holdings)
            assertEquals(1, holdings!!.size)

            val (holding, quote) = holdings[0]
            assertEquals("INFY", holding.symbol)
            assertEquals("INE009A01021", holding.isin)
            assertEquals(BigDecimal.valueOf(15), holding.quantity.value) // 10 + 5
            assertEquals(BigDecimal.valueOf(1400.0), holding.avgPriceReported?.amount)
            assertEquals(BigDecimal.valueOf(1500.0), quote.price.amount)
            assertEquals(BigDecimal.valueOf(1480.0), quote.prevClose.amount)

            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("GET", recordedRequest.method)
            assertEquals("/portfolio/holdings", recordedRequest.path)
            assertEquals("token my_key:my_token", recordedRequest.getHeader("Authorization"))
            assertEquals("3", recordedRequest.getHeader("X-Kite-Version"))
        }

    @Test
    fun http403FromBroker_invalidatesSessionAndShowsBanner() =
        runTest {
            credentialsRepo.saveCredentials("my_key", "my_secret")
            credentialsRepo.saveSession("my_token", "user_1")

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setBody("""{"status":"error","error_type":"TokenException","message":"Token expired"}"""),
            )

            val result = provider.fetchHoldings()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is KiteSessionExpiredException)

            // Verifies session is wiped locally on 403
            assertFalse(credentialsRepo.isSessionValid())
            assertTrue(provider.getSessionBannerState().isVisible)
        }

    @Test
    fun exchangeToken_computesChecksumAndSavesSession() =
        runTest {
            credentialsRepo.saveCredentials("test_key", "test_secret")

            val tokenResponseJson =
                """
                {
                  "status": "success",
                  "data": {
                    "user_id": "AB1234",
                    "access_token": "new_access_token_xyz",
                    "user_name": "Test Trader"
                  }
                }
                """.trimIndent()
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(tokenResponseJson))

            val result = provider.exchangeToken("test_request_token")
            assertTrue(result.isSuccess)

            assertEquals("new_access_token_xyz", credentialsRepo.getAccessToken())
            assertEquals("AB1234", credentialsRepo.getUserId())
            assertTrue(credentialsRepo.isSessionValid())
            assertFalse(provider.getSessionBannerState().isVisible)

            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("POST", recordedRequest.method)
            assertEquals("/session/token", recordedRequest.path)
            val body = recordedRequest.body.readUtf8()
            assertTrue(body.contains("api_key=test_key"))
            assertTrue(body.contains("request_token=test_request_token"))
            // Checksum verification
            val expectedChecksum = KiteAuthHelper.computeChecksum("test_key", "test_request_token", "test_secret")
            assertTrue(body.contains("checksum=$expectedChecksum"))
        }
}
