package com.personal.investmenttracker.data.broker.kotak

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.QuoteSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class KotakHoldingsMapperTest {
    @Test
    fun mapToDomain_withFullFields_mapsCorrectly() {
        val dto =
            KotakHoldingItemDto(
                symbol = "SBIN",
                isin = "INE062A01020",
                exchangeSegment = "bse_cm",
                holdingQuantity = "5",
                realisedQuantity = "0",
                averagePrice = "110.00",
                ltp = "120.00",
                closePrice = "120.00",
            )

        val timestamp = 1700000000000L
        val (holding, quote) = KotakHoldingsMapper.mapToDomain(dto, timestamp)

        assertEquals(Broker.KOTAK_NEO, holding.broker)
        assertEquals("INE062A01020", holding.isin)
        assertEquals("SBIN", holding.symbol)
        assertEquals(Exchange.BSE, holding.exchange)
        assertEquals(BigDecimal("5"), holding.quantity.value)
        assertNotNull(holding.avgPriceReported)
        assertEquals("110.00", holding.avgPriceReported!!.amount.toPlainString())

        assertEquals("120.00", quote.price.amount.toPlainString())
        assertEquals("120.00", quote.closePrice!!.amount.toPlainString())
        assertEquals("120.00", quote.prevClose.amount.toPlainString())
        assertEquals(QuoteSource.BROKER_LIVE, quote.source)
        assertEquals(Freshness.LIVE, quote.freshness)
        assertEquals(timestamp, quote.sourceTimestamp)
    }

    @Test
    fun mapToDomain_withNseSegment_mapsToNse() {
        val dto =
            KotakHoldingItemDto(
                symbol = "INFY",
                isin = "INE009A01021",
                exchangeSegment = "nse_cm",
                holdingQuantity = "50",
                averagePrice = "1450.00",
                ltp = "1520.00",
            )

        val (holding, quote) = KotakHoldingsMapper.mapToDomain(dto)

        assertEquals(Exchange.NSE, holding.exchange)
        assertEquals("INFY", holding.symbol)
        assertEquals(BigDecimal("50"), holding.quantity.value)
        // closePrice should fallback to ltp when null
        assertEquals("1520.00", quote.closePrice!!.amount.toPlainString())
    }

    @Test
    fun mapToDomain_withZeroAvgPrice_mapsToNullCost() {
        val dto =
            KotakHoldingItemDto(
                symbol = "BONUS",
                isin = "INE999A01019",
                holdingQuantity = "10",
                averagePrice = "0.00",
                ltp = "50.00",
            )

        val (holding, _) = KotakHoldingsMapper.mapToDomain(dto)

        assertNull(holding.avgPriceReported)
        assertEquals(BigDecimal("10"), holding.quantity.value)
    }

    @Test
    fun mapAllToDomain_mapsListCorrectly() {
        val dtos =
            listOf(
                KotakHoldingItemDto(symbol = "SBIN", isin = "INE062A01020", holdingQuantity = "5"),
                KotakHoldingItemDto(symbol = "TCS", isin = "INE467B01029", holdingQuantity = "20"),
            )

        val pairs = KotakHoldingsMapper.mapAllToDomain(dtos)
        assertEquals(2, pairs.size)
        assertEquals("SBIN", pairs[0].first.symbol)
        assertEquals("TCS", pairs[1].first.symbol)
    }
}
