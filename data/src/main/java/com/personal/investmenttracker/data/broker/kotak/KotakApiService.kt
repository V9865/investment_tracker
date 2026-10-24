package com.personal.investmenttracker.data.broker.kotak

import retrofit2.http.GET
import retrofit2.http.Header

interface KotakApiService {
    @GET("portfolio/holdings")
    suspend fun getHoldings(
        @Header("Authorization") authorization: String,
        @Header("Auth") auth: String,
        @Header("neo-fin-key") neoFinKey: String = "neotradeapi",
        @Header("Sid") sid: String? = null,
    ): KotakHoldingsResponse

    @GET("portfolio/v1/holdings")
    suspend fun getHoldingsV1(
        @Header("Authorization") authorization: String,
        @Header("Auth") auth: String,
        @Header("neo-fin-key") neoFinKey: String = "neotradeapi",
        @Header("Sid") sid: String? = null,
    ): KotakHoldingsResponse
}
