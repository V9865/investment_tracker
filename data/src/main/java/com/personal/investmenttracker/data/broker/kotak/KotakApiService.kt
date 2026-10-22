package com.personal.investmenttracker.data.broker.kotak

import retrofit2.http.GET
import retrofit2.http.Header

interface KotakApiService {
    @GET("portfolio/holdings")
    suspend fun getHoldings(
        @Header("Authorization") authorization: String,
        @Header("Sid") sid: String? = null,
    ): KotakHoldingsResponse
}
