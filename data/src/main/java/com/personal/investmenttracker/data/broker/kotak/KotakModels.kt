package com.personal.investmenttracker.data.broker.kotak

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KotakHoldingsResponse(
    val stat: String? = null,
    val stCode: Int? = null,
    val data: List<KotakHoldingItemDto> = emptyList(),
)

@Serializable
data class KotakHoldingItemDto(
    val symbol: String,
    val isin: String,
    @SerialName("exchangeSegment")
    val exchangeSegment: String = "nse_cm",
    @SerialName("holdingQuantity")
    val holdingQuantity: String = "0",
    @SerialName("realisedQuantity")
    val realisedQuantity: String = "0",
    @SerialName("averagePrice")
    val averagePrice: String = "0",
    val ltp: String = "0",
    @SerialName("closePrice")
    val closePrice: String? = null,
    val pnl: String? = null,
    @SerialName("companyName")
    val companyName: String? = null,
    val product: String? = null,
)
