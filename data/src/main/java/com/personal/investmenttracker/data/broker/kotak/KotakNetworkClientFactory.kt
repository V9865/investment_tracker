package com.personal.investmenttracker.data.broker.kotak

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object KotakNetworkClientFactory {
    const val KOTAK_BASE_URL = "https://napi.kotaksecurities.com/"

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    fun createOkHttpClient(
        allowListInterceptor: KotakReadOnlyAllowListInterceptor =
            KotakReadOnlyAllowListInterceptor(),
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(allowListInterceptor)
            .build()

    fun createRetrofit(
        okHttpClient: OkHttpClient,
        baseUrl: String = KOTAK_BASE_URL,
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
        baseUrl: String = KOTAK_BASE_URL,
        allowListInterceptor: KotakReadOnlyAllowListInterceptor = KotakReadOnlyAllowListInterceptor(),
    ): KotakApiService {
        val client = createOkHttpClient(allowListInterceptor)
        val retrofit = createRetrofit(client, baseUrl)
        return retrofit.create(KotakApiService::class.java)
    }
}
