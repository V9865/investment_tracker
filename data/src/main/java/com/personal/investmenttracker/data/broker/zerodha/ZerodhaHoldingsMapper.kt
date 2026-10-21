package com.personal.investmenttracker.data.broker.zerodha

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import java.math.BigDecimal

object ZerodhaHoldingsMapper {
    fun mapToDomain(
        dto: KiteHoldingItemDto,
        timestampMs: Long = System.currentTimeMillis(),
    ): Pair<Holding, Quote> {
        val exchange =
            if (dto.exchange.equals("BSE", ignoreCase = true)) {
                Exchange.BSE
            } else {
                Exchange.NSE
            }

        val lastPriceMoney = Money(BigDecimal.valueOf(dto.lastPrice))
        val prevCloseMoney = Money(BigDecimal.valueOf(dto.closePrice))

        val quote =
            Quote(
                price = lastPriceMoney,
                prevClose = prevCloseMoney,
                closePrice = prevCloseMoney,
                source = QuoteSource.BROKER_LIVE,
                sourceTimestamp = timestampMs,
                freshness = Freshness.LIVE,
            )

        val avgPriceMoney =
            if (dto.averagePrice > 0.0) {
                Money(BigDecimal.valueOf(dto.averagePrice))
            } else {
                null
            }

        val totalQuantity = dto.quantity + dto.t1Quantity

        val holding =
            Holding(
                broker = Broker.ZERODHA,
                isin = dto.isin.trim(),
                exchange = exchange,
                symbol = dto.tradingsymbol.trim(),
                quantity = Quantity(BigDecimal.valueOf(totalQuantity)),
                avgPriceReported = avgPriceMoney,
                quote = quote,
            )

        return Pair(holding, quote)
    }

    fun mapAllToDomain(
        dtos: List<KiteHoldingItemDto>,
        timestampMs: Long = System.currentTimeMillis(),
    ): List<Pair<Holding, Quote>> = dtos.map { mapToDomain(it, timestampMs) }
}
