package com.personal.investmenttracker.data.broker.kotak

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import java.math.BigDecimal

object KotakHoldingsMapper {
    fun mapToDomain(
        dto: KotakHoldingItemDto,
        timestampMs: Long = System.currentTimeMillis(),
    ): Pair<Holding, Quote> {
        val exchange =
            if (dto.exchangeSegment.contains("bse", ignoreCase = true)) {
                Exchange.BSE
            } else {
                Exchange.NSE
            }

        val ltpAmount = parseBigDecimalSafely(dto.ltp)
        val closeAmount = dto.closePrice?.let { parseBigDecimalSafely(it) } ?: ltpAmount

        val ltpMoney = Money(ltpAmount)
        val closeMoney = Money(closeAmount)

        val quote =
            Quote(
                price = ltpMoney,
                prevClose = closeMoney,
                closePrice = closeMoney,
                source = QuoteSource.BROKER_LIVE,
                sourceTimestamp = timestampMs,
                freshness = Freshness.LIVE,
            )

        val holdingQty = parseBigDecimalSafely(dto.holdingQuantity)
        val realisedQty = parseBigDecimalSafely(dto.realisedQuantity)
        val totalQty = holdingQty.add(realisedQty)

        val avgAmount = parseBigDecimalSafely(dto.averagePrice)
        val avgPriceMoney =
            if (avgAmount.compareTo(BigDecimal.ZERO) > 0) {
                Money(avgAmount)
            } else {
                null
            }

        val holding =
            Holding(
                broker = Broker.KOTAK_NEO,
                isin = dto.isin.trim(),
                exchange = exchange,
                symbol = dto.symbol.trim(),
                quantity = Quantity(totalQty),
                avgPriceReported = avgPriceMoney,
                quote = quote,
            )

        return Pair(holding, quote)
    }

    fun mapAllToDomain(
        dtos: List<KotakHoldingItemDto>,
        timestampMs: Long = System.currentTimeMillis(),
    ): List<Pair<Holding, Quote>> = dtos.map { mapToDomain(it, timestampMs) }

    private fun parseBigDecimalSafely(value: String): BigDecimal =
        try {
            val clean = value.replace(",", "").trim()
            if (clean.isBlank()) BigDecimal.ZERO else BigDecimal(clean)
        } catch (_: NumberFormatException) {
            BigDecimal.ZERO
        }
}
