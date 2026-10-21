package com.personal.investmenttracker.data.broker.zerodha

import okhttp3.Interceptor
import okhttp3.Response

class KiteReadOnlyAllowListInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val method = request.method

        if (!isAllowed(method, path)) {
            throw KiteForbiddenEndpointException(
                "READ-ONLY VIOLATION: Endpoint [$method $path] is strictly prohibited. " +
                    "Only read-only portfolio endpoints are allowed.",
            )
        }

        val enrichedRequest =
            request
                .newBuilder()
                .header(HEADER_KITE_VERSION, KITE_VERSION_VALUE)
                .build()

        return chain.proceed(enrichedRequest)
    }

    private fun isAllowed(
        method: String,
        path: String,
    ): Boolean {
        val normalizedPath = path.trimEnd('/')
        return when {
            method.equals("POST", ignoreCase = true) && normalizedPath == "/session/token" -> true
            method.equals("GET", ignoreCase = true) && normalizedPath == "/portfolio/holdings" -> true
            method.equals("GET", ignoreCase = true) && normalizedPath == "/portfolio/positions" -> true
            else -> false
        }
    }

    companion object {
        const val HEADER_KITE_VERSION = "X-Kite-Version"
        const val KITE_VERSION_VALUE = "3"
    }
}
