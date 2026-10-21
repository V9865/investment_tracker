package com.personal.investmenttracker.data.broker.zerodha

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface KiteApiService {
    @FormUrlEncoded
    @POST("session/token")
    suspend fun exchangeToken(
        @Field("api_key") apiKey: String,
        @Field("request_token") requestToken: String,
        @Field("checksum") checksum: String,
    ): KiteTokenResponse

    @GET("portfolio/holdings")
    suspend fun getHoldings(): KiteHoldingsResponse
}
