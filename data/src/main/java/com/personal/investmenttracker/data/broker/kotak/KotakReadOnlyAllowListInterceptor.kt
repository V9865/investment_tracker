package com.personal.investmenttracker.data.broker.kotak

import okhttp3.Interceptor
import okhttp3.Response

class KotakReadOnlyAllowListInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val method = request.method

        if (!isAllowed(method, path)) {
            throw KotakForbiddenEndpointException(
                "READ-ONLY VIOLATION: Kotak endpoint [$method $path] is strictly prohibited. " +
                    "Only read-only portfolio endpoints are permitted.",
            )
        }

        return chain.proceed(request)
    }

    private fun isAllowed(
        method: String,
        path: String,
    ): Boolean {
        if (!method.equals("GET", ignoreCase = true)) return false
        val normalizedPath = path.trimEnd('/')
        return normalizedPath == "/portfolio/holdings" ||
            normalizedPath == "/holdings" ||
            normalizedPath == "/portfolio/positions"
    }
}
