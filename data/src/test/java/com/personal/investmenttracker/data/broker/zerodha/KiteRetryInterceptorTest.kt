package com.personal.investmenttracker.data.broker.zerodha

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class KiteRetryInterceptorTest {
    private lateinit var mockWebServer: MockWebServer
    private var sleepCount = 0
    private var totalSleepTimeMs = 0L

    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        sleepCount = 0
        totalSleepTimeMs = 0L

        val retryInterceptor =
            KiteRetryInterceptor(
                maxRetries = 2,
                initialDelayMs = 50L,
                maxJitterMs = 10L,
                sleeper = { millis ->
                    sleepCount++
                    totalSleepTimeMs += millis
                },
            )

        okHttpClient =
            OkHttpClient
                .Builder()
                .addInterceptor(retryInterceptor)
                .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun http403_throwsSessionExpiredExceptionImmediatelyWithoutRetry() {
        val errorBody = """{"status":"error","error_type":"TokenException"}"""
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody(errorBody))

        val request =
            Request
                .Builder()
                .url(mockWebServer.url("/portfolio/holdings"))
                .get()
                .build()

        val exception =
            assertThrows(KiteSessionExpiredException::class.java) {
                okHttpClient.newCall(request).execute()
            }

        assert(exception.message!!.contains("Kite session expired"))
        assertEquals(1, mockWebServer.requestCount)
        assertEquals(0, sleepCount)
    }

    @Test
    fun http429_retriesAndSucceedsWhenNextRequestIs200() {
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"success"}"""))

        val request =
            Request
                .Builder()
                .url(mockWebServer.url("/portfolio/holdings"))
                .get()
                .build()

        val response = okHttpClient.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(2, mockWebServer.requestCount)
        assertEquals(1, sleepCount)
    }

    @Test
    fun http429_exhaustsRetriesAndThrowsRateLimitException() {
        // Enqueue 429 for initial + 2 retries (3 total)
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("Rate limit"))
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("Rate limit"))
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("Rate limit"))

        val request =
            Request
                .Builder()
                .url(mockWebServer.url("/portfolio/holdings"))
                .get()
                .build()

        val exception =
            assertThrows(KiteRateLimitException::class.java) {
                okHttpClient.newCall(request).execute()
            }

        assert(exception.message!!.contains("rate limit (HTTP 429) exceeded"))
        assertEquals(3, mockWebServer.requestCount)
        assertEquals(2, sleepCount)
    }
}
