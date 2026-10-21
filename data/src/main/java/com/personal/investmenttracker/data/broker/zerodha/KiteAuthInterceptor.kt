package com.personal.investmenttracker.data.broker.zerodha

import okhttp3.Interceptor
import okhttp3.Response

interface KiteSessionTokenProvider {
    fun getApiKey(): String?

    fun getAccessToken(): String?
}

class KiteAuthInterceptor(
    private val tokenProvider: KiteSessionTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath.trimEnd('/')

        if (path == "/session/token") {
            return chain.proceed(request)
        }

        val apiKey = tokenProvider.getApiKey()
        val accessToken = tokenProvider.getAccessToken()

        return if (!apiKey.isNullOrBlank() && !accessToken.isNullOrBlank()) {
            val authenticatedRequest =
                request
                    .newBuilder()
                    .header(HEADER_AUTHORIZATION, "token $apiKey:$accessToken")
                    .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(request)
        }
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}
