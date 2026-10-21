package com.personal.investmenttracker.data.broker.zerodha

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.QuoteSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ZerodhaHoldingsMapperTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun parseAndMapOfficialKiteHoldingsJson() {
        val jsonPayload =
            """
            {
              "status": "success",
              "data": [
                {
                  "tradingsymbol": "AARON",
                  "exchange": "NSE",
                  "instrument_token": 263681,
                  "isin": "INE721Z01010",
                  "product": "CNC",
                  "price": 0,
                  "quantity": 1,
                  "used_quantity": 0,
                  "t1_quantity": 0,
                  "realised_quantity": 1,
                  "average_price": 161.0,
                  "last_price": 352.95,
                  "close_price": 352.35,
                  "pnl": 191.95
                },
                {
                  "tradingsymbol": "SBIN",
                  "exchange": "BSE",
                  "instrument_token": 128028676,
                  "isin": "INE062A01020",
                  "product": "CNC",
                  "price": 0,
                  "quantity": 16,
                  "used_quantity": 0,
                  "t1_quantity": 2,
                  "realised_quantity": 16,
                  "average_price": 801.78,
                  "last_price": 762.45,
                  "close_price": 766.40,
                  "pnl": -629.30
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString<KiteHoldingsResponse>(jsonPayload)
        assertEquals("success", response.status)
        assertEquals(2, response.data.size)

        val mapped = ZerodhaHoldingsMapper.mapAllToDomain(response.data, timestampMs = 1700000000000L)
        assertEquals(2, mapped.size)

        // Item 1: AARON NSE
        val (holding1, quote1) = mapped[0]
        assertEquals(Broker.ZERODHA, holding1.broker)
        assertEquals("INE721Z01010", holding1.isin)
        assertEquals(Exchange.NSE, holding1.exchange)
        assertEquals("AARON", holding1.symbol)
        assertEquals(BigDecimal.valueOf(1), holding1.quantity.value)
        assertEquals(BigDecimal.valueOf(161.0), holding1.avgPriceReported?.amount)
        assertEquals(BigDecimal.valueOf(352.95), quote1.price.amount)
        assertEquals(BigDecimal.valueOf(352.35), quote1.prevClose.amount)
        assertEquals(BigDecimal.valueOf(352.35), quote1.closePrice?.amount)
        assertEquals(QuoteSource.BROKER_LIVE, quote1.source)
        assertEquals(Freshness.LIVE, quote1.freshness)

        // Item 2: SBIN BSE (quantity 16 + t1 2 = 18)
        val (holding2, quote2) = mapped[1]
        assertEquals(Broker.ZERODHA, holding2.broker)
        assertEquals("INE062A01020", holding2.isin)
        assertEquals(Exchange.BSE, holding2.exchange)
        assertEquals("SBIN", holding2.symbol)
        assertEquals(BigDecimal.valueOf(18), holding2.quantity.value)
        assertEquals(BigDecimal.valueOf(801.78), holding2.avgPriceReported?.amount)
        assertEquals(BigDecimal.valueOf(762.45), quote2.price.amount)
        assertEquals(BigDecimal.valueOf(766.4), quote2.prevClose.amount)
    }

    @Test
    fun zeroAveragePrice_mapsToNullCostUnknown() {
        val dto =
            KiteHoldingItemDto(
                tradingsymbol = "BONUS",
                exchange = "NSE",
                isin = "INE123B01010",
                quantity = 10,
                averagePrice = 0.0,
                lastPrice = 100.0,
                closePrice = 98.0,
            )

        val (holding, quote) = ZerodhaHoldingsMapper.mapToDomain(dto)

        assertNull(holding.avgPriceReported)
        assertTrue(holding.isCostUnknown)
        assertEquals(BigDecimal.valueOf(100.0), quote.price.amount)
        assertNotNull(quote.prevClose)
    }
}
