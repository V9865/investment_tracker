package com.personal.investmenttracker.data.broker.zerodha

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object KiteNetworkClientFactory {
    const val KITE_BASE_URL = "https://api.kite.trade/"

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    fun createOkHttpClient(
        tokenProvider: KiteSessionTokenProvider,
        retryInterceptor: KiteRetryInterceptor = KiteRetryInterceptor(),
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(KiteReadOnlyAllowListInterceptor())
            .addInterceptor(KiteAuthInterceptor(tokenProvider))
            .addInterceptor(retryInterceptor)
            .build()

    fun createRetrofit(
        okHttpClient: OkHttpClient,
        baseUrl: String = KITE_BASE_URL,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    fun createApiService(
        tokenProvider: KiteSessionTokenProvider,
        baseUrl: String = KITE_BASE_URL,
        retryInterceptor: KiteRetryInterceptor = KiteRetryInterceptor(),
    ): KiteApiService {
        val client = createOkHttpClient(tokenProvider, retryInterceptor)
        val retrofit = createRetrofit(client, baseUrl)
        return retrofit.create(KiteApiService::class.java)
    }
}
