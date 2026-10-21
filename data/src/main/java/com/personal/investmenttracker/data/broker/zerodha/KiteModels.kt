package com.personal.investmenttracker.data.broker.zerodha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KiteHoldingsResponse(
    val status: String,
    val data: List<KiteHoldingItemDto> = emptyList(),
)

@Serializable
data class KiteHoldingItemDto(
    val tradingsymbol: String,
    val exchange: String,
    val isin: String,
    val quantity: Long,
    @SerialName("average_price")
    val averagePrice: Double,
    @SerialName("last_price")
    val lastPrice: Double,
    @SerialName("close_price")
    val closePrice: Double,
    val pnl: Double? = null,
    @SerialName("day_change")
    val dayChange: Double? = null,
    @SerialName("day_change_percentage")
    val dayChangePercentage: Double? = null,
    @SerialName("t1_quantity")
    val t1Quantity: Long = 0,
    @SerialName("realised_quantity")
    val realisedQuantity: Long = 0,
    val product: String? = null,
)

@Serializable
data class KiteTokenResponse(
    val status: String,
    val data: KiteTokenDataDto,
)

@Serializable
data class KiteTokenDataDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String? = null,
    val email: String? = null,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("public_token")
    val publicToken: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("login_time")
    val loginTime: String? = null,
)

@Serializable
data class KiteErrorResponse(
    val status: String? = null,
    val message: String? = null,
    @SerialName("error_type")
    val errorType: String? = null,
)
