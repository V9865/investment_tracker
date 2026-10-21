package com.personal.investmenttracker.domain.model

data class ListingKey(
    val isin: String,
    val exchange: Exchange,
)

data class Holding(
    val broker: Broker,
    val isin: String,
    val exchange: Exchange,
    val symbol: String,
    val quantity: Quantity,
    val avgPriceReported: Money?,
    val quote: Quote? = null,
) {
    val listingKey: ListingKey
        get() = ListingKey(isin, exchange)

    val isCostUnknown: Boolean
        get() = avgPriceReported == null || avgPriceReported.amount.compareTo(java.math.BigDecimal.ZERO) == 0

    val isZeroQty: Boolean
        get() = quantity.isZero

    val isNegativeQty: Boolean
        get() = quantity.isNegative
}
