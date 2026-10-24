package com.personal.investmenttracker.data.broker.kotak

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KotakReadOnlyAllowListTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient =
            OkHttpClient
                .Builder()
                .addInterceptor(KotakReadOnlyAllowListInterceptor())
                .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun getPortfolioHoldings_isAllowed() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"stat":"Ok","data":[]}"""))

        val request =
            Request
                .Builder()
                .url(mockWebServer.url("/portfolio/holdings"))
                .get()
                .build()

        val response = okHttpClient.newCall(request).execute()
        assertEquals(200, response.code)

        val recorded = mockWebServer.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/portfolio/holdings", recorded.path)
    }

    @Test
    fun getHoldingsVariants_areAllowed() {
        val paths = listOf("/portfolio/v1/holdings", "/holdings", "/portfolio/positions")
        for (path in paths) {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"stat":"Ok","data":[]}"""))
            val request =
                Request
                    .Builder()
                    .url(mockWebServer.url(path))
                    .get()
                    .build()

            val response = okHttpClient.newCall(request).execute()
            assertEquals(200, response.code)
            val recorded = mockWebServer.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals(path, recorded.path)
        }
    }

    @Test
    fun forbiddenEndpoints_areBlockedWithSecurityException() {
        val forbiddenEndpoints =
            listOf(
                Pair("POST", "/orders"),
                Pair("POST", "/orders/amo"),
                Pair("POST", "/orders/regular"),
                Pair("PUT", "/orders/123"),
                Pair("DELETE", "/orders/123"),
                Pair("POST", "/orders/modify"),
                Pair("POST", "/orders/cancel"),
                Pair("POST", "/positions/convert"),
                Pair("GET", "/funds"),
                Pair("POST", "/funds"),
                Pair("GET", "/trades"),
                Pair("POST", "/login/1.0/tradeApiValidate"),
            )

        for ((method, path) in forbiddenEndpoints) {
            val requestBuilder = Request.Builder().url(mockWebServer.url(path))
            val request =
                when (method) {
                    "POST" -> requestBuilder.post("{}".toRequestBody()).build()
                    "PUT" -> requestBuilder.put("{}".toRequestBody()).build()
                    "DELETE" -> requestBuilder.delete().build()
                    else -> requestBuilder.get().build()
                }

            val exception =
                assertThrows(KotakForbiddenEndpointException::class.java) {
                    okHttpClient.newCall(request).execute()
                }
            assertTrue(exception.message!!.contains("READ-ONLY VIOLATION"))
        }

        // Verify that NO forbidden requests reached the mock server
        assertEquals(0, mockWebServer.requestCount)
    }
}
