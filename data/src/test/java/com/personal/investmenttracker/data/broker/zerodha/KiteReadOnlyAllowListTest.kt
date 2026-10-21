package com.personal.investmenttracker.data.broker.zerodha

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class KiteReadOnlyAllowListTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient =
            OkHttpClient
                .Builder()
                .addInterceptor(KiteReadOnlyAllowListInterceptor())
                .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun getPortfolioHoldings_isAllowed() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"success"}"""))

        val request =
            Request
                .Builder()
                .url(mockWebServer.url("/portfolio/holdings"))
                .get()
                .build()

        val response = okHttpClient.newCall(request).execute()
        assertEquals(200, response.code)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/portfolio/holdings", recordedRequest.path)
        assertEquals("3", recordedRequest.getHeader(KiteReadOnlyAllowListInterceptor.HEADER_KITE_VERSION))
    }

    @Test
    fun postSessionToken_isAllowed() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"success"}"""))

        val request =
            Request
                .Builder()
                .url(mockWebServer.url("/session/token"))
                .post("api_key=test&request_token=xyz&checksum=abc".toRequestBody())
                .build()

        val response = okHttpClient.newCall(request).execute()
        assertEquals(200, response.code)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/session/token", recordedRequest.path)
        assertEquals("3", recordedRequest.getHeader(KiteReadOnlyAllowListInterceptor.HEADER_KITE_VERSION))
    }

    @Test
    fun postOrders_isBlockedWithSecurityException() {
        val forbiddenEndpoints =
            listOf(
                Pair("POST", "/orders/regular"),
                Pair("POST", "/orders/amo"),
                Pair("PUT", "/orders/regular/12345"),
                Pair("DELETE", "/orders/regular/12345"),
                Pair("GET", "/orders"),
                Pair("GET", "/trades"),
                Pair("PUT", "/portfolio/positions/convert"),
                Pair("GET", "/user/margins"),
                Pair("GET", "/user/funds"),
                Pair("POST", "/gtt/triggers"),
            )

        for ((method, path) in forbiddenEndpoints) {
            val requestBuilder = Request.Builder().url(mockWebServer.url(path))
            val request =
                when (method) {
                    "POST" -> requestBuilder.post("".toRequestBody()).build()
                    "PUT" -> requestBuilder.put("".toRequestBody()).build()
                    "DELETE" -> requestBuilder.delete().build()
                    else -> requestBuilder.get().build()
                }

            val exception =
                assertThrows(KiteForbiddenEndpointException::class.java) {
                    okHttpClient.newCall(request).execute()
                }
            assert(exception.message!!.contains("READ-ONLY VIOLATION"))
        }

        // Verify that NO forbidden requests reached the mock server
        assertEquals(0, mockWebServer.requestCount)
    }
}
