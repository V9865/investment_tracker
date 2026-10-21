package com.personal.investmenttracker.data.broker.zerodha

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.random.Random

class KiteRetryInterceptor(
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val maxJitterMs: Long = DEFAULT_MAX_JITTER_MS,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isGet = request.method.equals("GET", ignoreCase = true)

        for (attempt in 0..maxRetries) {
            val response = executeAttempt(chain, request, isGet, attempt)
            if (response != null) {
                return response
            }
            performBackoff(attempt)
        }

        throw KiteNetworkTimeoutException("Kite request failed after $maxRetries attempts")
    }

    private fun executeAttempt(
        chain: Interceptor.Chain,
        request: Request,
        isGet: Boolean,
        attempt: Int,
    ): Response? {
        val response = proceedSafely(chain, request, isGet, attempt) ?: return null
        return evaluateResponse(response, isGet, attempt)
    }

    private fun proceedSafely(
        chain: Interceptor.Chain,
        request: Request,
        isGet: Boolean,
        attempt: Int,
    ): Response? {
        var response: Response? = null
        try {
            response = chain.proceed(request)
        } catch (e: SocketTimeoutException) {
            handleTimeout(e, isGet, attempt)
        } catch (e: InterruptedIOException) {
            handleTimeout(e, isGet, attempt)
        }
        return response
    }

    private fun handleTimeout(
        e: IOException,
        isGet: Boolean,
        attempt: Int,
    ) {
        if (!isGet || attempt >= maxRetries) {
            throw KiteNetworkTimeoutException("Network timeout during Kite API request", e)
        }
    }

    private fun evaluateResponse(
        response: Response,
        isGet: Boolean,
        attempt: Int,
    ): Response? {
        if (response.code == HTTP_FORBIDDEN) {
            response.close()
            throw KiteSessionExpiredException("Kite session expired or invalid (HTTP 403). Please re-login.")
        }

        if (response.code == HTTP_TOO_MANY_REQUESTS && isGet) {
            response.close()
            if (attempt >= maxRetries) {
                throw KiteRateLimitException("Kite rate limit (HTTP 429) exceeded after $maxRetries retries.")
            }
            return null
        }

        return response
    }

    private fun performBackoff(attempt: Int) {
        val backoffMultiplier = 1L shl attempt
        val jitter = if (maxJitterMs > 0) Random.nextLong(0, maxJitterMs) else 0L
        val sleepTimeMs = (initialDelayMs * backoffMultiplier) + jitter
        sleeper(sleepTimeMs)
    }

    companion object {
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_INITIAL_DELAY_MS = 200L
        const val DEFAULT_MAX_JITTER_MS = 100L

        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
