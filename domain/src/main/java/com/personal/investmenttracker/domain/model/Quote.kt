package com.personal.investmenttracker.domain.model

enum class Broker(
    val displayName: String,
) {
    ZERODHA("Zerodha Kite"),
    KOTAK_NEO("Kotak Neo"),
}

enum class Exchange {
    NSE,
    BSE,
}

enum class QuoteSource {
    BROKER_LIVE,
    BROKER_CACHE,
    EOD_OFFICIAL,
}

enum class Freshness {
    LIVE,
    DELAYED,
    STALE,
}

data class Quote(
    val price: Money,
    val prevClose: Money,
    val closePrice: Money? = null,
    val source: QuoteSource,
    val sourceTimestamp: Long,
    val freshness: Freshness,
) {
    val isStale: Boolean
        get() = freshness == Freshness.STALE
}
