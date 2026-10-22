package com.personal.investmenttracker.data.sync

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.PnlCompleteness
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import com.personal.investmenttracker.domain.model.SnapshotItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class DailyPnlCalculatorTest {
    private data class HoldingTestParam(
        val broker: Broker = Broker.ZERODHA,
        val isin: String = "INE002A01018",
        val exchange: Exchange = Exchange.NSE,
        val symbol: String = "RELIANCE",
        val qty: String = "10",
        val avgPrice: String? = null,
        val lastPrice: String = "100.00",
        val prevClose: String = "100.00",
        val closePrice: String? = null,
        val freshness: Freshness = Freshness.LIVE,
    )

    private fun createHolding(param: HoldingTestParam): Holding {
        val quote =
            Quote(
                price = Money(BigDecimal(param.lastPrice)),
                prevClose = Money(BigDecimal(param.prevClose)),
                closePrice = param.closePrice?.let { Money(BigDecimal(it)) },
                source = QuoteSource.BROKER_LIVE,
                sourceTimestamp = System.currentTimeMillis(),
                freshness = param.freshness,
            )
        return Holding(
            broker = param.broker,
            isin = param.isin,
            exchange = param.exchange,
            symbol = param.symbol,
            quantity = Quantity(BigDecimal(param.qty)),
            avgPriceReported = param.avgPrice?.let { Money(BigDecimal(it)) },
            quote = quote,
        )
    }

    @Test
    fun `worked example 1 - 10 sh prev close 100 close 103 daily 30`() {
        val holding =
            createHolding(
                HoldingTestParam(
                    qty = "10",
                    avgPrice = "100.00",
                    lastPrice = "103.00",
                    prevClose = "100.00",
                    closePrice = "103.00",
                ),
            )

        // Without prior snapshot, estimated from prev close
        val pnl =
            DailyPnlCalculator.calculateDailyPnl(
                DailyPnlCalculator.CalculationInput(
                    date = "2026-10-22",
                    currentHoldings = listOf(holding),
                ),
            )

        assertEquals(Money(BigDecimal("1030.00")), pnl.totalValue)
        assertEquals(Money(BigDecimal("30.00")), pnl.pnlAmount)
        assertEquals(PnlCompleteness.ESTIMATED, pnl.completeness)
    }

    @Test
    fun `worked example 2 - held 10 prev close 100 bought 10 today at 102 close 103 daily 40`() {
        val yestItem =
            SnapshotItem(
                isin = "INE002A01018",
                exchange = Exchange.NSE,
                symbol = "RELIANCE",
                broker = Broker.ZERODHA,
                quantity = Quantity(BigDecimal("10")),
                closePrice = Money(BigDecimal("100.00")),
                value = Money(BigDecimal("1000.00")),
                sourceTimestamp = 1000L,
            )

        val todayHolding =
            createHolding(
                HoldingTestParam(
                    qty = "20",
                    avgPrice = "102.00",
                    lastPrice = "103.00",
                    prevClose = "100.00",
                    closePrice = "103.00",
                ),
            )

        val pnl =
            DailyPnlCalculator.calculateDailyPnl(
                DailyPnlCalculator.CalculationInput(
                    date = "2026-10-22",
                    currentHoldings = listOf(todayHolding),
                    yesterdaySnapshotItems = listOf(yestItem),
                ),
            )

        assertEquals(Money(BigDecimal("2060.00")), pnl.totalValue)
        assertEquals(Money(BigDecimal("1020.00")), pnl.netTradeCashFlow)
        assertEquals(Money(BigDecimal("40.00")), pnl.pnlAmount)
        assertEquals(PnlCompleteness.COMPLETE, pnl.completeness)

        val crossCheck =
            DailyPnlCalculator.crossCheckDailyPnl(
                validHoldings = listOf(todayHolding),
                yesterdaySnapshotItems = listOf(yestItem),
            )
        assertEquals(Money(BigDecimal("40.00")), crossCheck)
    }

    @Test
    fun `worked example 3 - snapshot method yesterday 1000 today 1130 after buying 100 worth daily 30`() {
        val yestItem =
            SnapshotItem(
                isin = "INE002A01018",
                exchange = Exchange.NSE,
                symbol = "RELIANCE",
                broker = Broker.ZERODHA,
                quantity = Quantity(BigDecimal("10")),
                closePrice = Money(BigDecimal("100.00")),
                value = Money(BigDecimal("1000.00")),
                sourceTimestamp = 1000L,
            )

        val heldHolding =
            createHolding(
                HoldingTestParam(
                    isin = "INE002A01018",
                    qty = "10",
                    avgPrice = "100.00",
                    lastPrice = "103.00",
                    prevClose = "100.00",
                    closePrice = "103.00",
                ),
            )
        val boughtHolding =
            createHolding(
                HoldingTestParam(
                    isin = "INE009A01021",
                    symbol = "INFY",
                    qty = "1",
                    avgPrice = "100.00",
                    lastPrice = "100.00",
                    prevClose = "100.00",
                    closePrice = "100.00",
                ),
            )

        val pnl =
            DailyPnlCalculator.calculateDailyPnl(
                DailyPnlCalculator.CalculationInput(
                    date = "2026-10-22",
                    currentHoldings = listOf(heldHolding, boughtHolding),
                    yesterdaySnapshotItems = listOf(yestItem),
                ),
            )

        assertEquals(Money(BigDecimal("1130.00")), pnl.totalValue)
        assertEquals(Money(BigDecimal("100.00")), pnl.netTradeCashFlow)
        assertEquals(Money(BigDecimal("30.00")), pnl.pnlAmount)
    }

    @Test
    fun `worked example 4 - consolidated ISIN across both brokers`() {
        val zerodha =
            createHolding(
                HoldingTestParam(
                    broker = Broker.ZERODHA,
                    isin = "INE062A01020",
                    exchange = Exchange.NSE,
                    symbol = "SBIN",
                    qty = "10",
                    avgPrice = "100.00",
                    lastPrice = "120.00",
                    prevClose = "115.00",
                ),
            )
        val kotak =
            createHolding(
                HoldingTestParam(
                    broker = Broker.KOTAK_NEO,
                    isin = "INE062A01020",
                    exchange = Exchange.NSE,
                    symbol = "SBIN",
                    qty = "5",
                    avgPrice = "110.00",
                    lastPrice = "120.00",
                    prevClose = "115.00",
                ),
            )

        val pnl =
            DailyPnlCalculator.calculateDailyPnl(
                DailyPnlCalculator.CalculationInput(
                    date = "2026-10-22",
                    currentHoldings = listOf(zerodha, kotak),
                ),
            )

        assertEquals(Money(BigDecimal("1800.00")), pnl.totalValue)
        assertEquals(Money(BigDecimal("75.00")), pnl.pnlAmount)
    }

    @Test
    fun `stale or missing quote marks completeness as ESTIMATED`() {
        val freshHolding =
            createHolding(
                HoldingTestParam(
                    isin = "INE002A01018",
                    qty = "10",
                    lastPrice = "100.00",
                    prevClose = "95.00",
                    freshness = Freshness.LIVE,
                ),
            )
        val staleHolding =
            createHolding(
                HoldingTestParam(
                    isin = "INE040A01034",
                    symbol = "HDFCBANK",
                    qty = "5",
                    lastPrice = "1500.00",
                    prevClose = "1480.00",
                    freshness = Freshness.STALE,
                ),
            )

        val yestItem1 =
            SnapshotItem(
                isin = "INE002A01018",
                exchange = Exchange.NSE,
                symbol = "RELIANCE",
                broker = Broker.ZERODHA,
                quantity = Quantity(BigDecimal("10")),
                closePrice = Money(BigDecimal("95.00")),
                value = Money(BigDecimal("950.00")),
                sourceTimestamp = 1000L,
            )
        val yestItem2 =
            SnapshotItem(
                isin = "INE040A01034",
                exchange = Exchange.NSE,
                symbol = "HDFCBANK",
                broker = Broker.ZERODHA,
                quantity = Quantity(BigDecimal("5")),
                closePrice = Money(BigDecimal("1480.00")),
                value = Money(BigDecimal("7400.00")),
                sourceTimestamp = 1000L,
            )

        val pnl =
            DailyPnlCalculator.calculateDailyPnl(
                DailyPnlCalculator.CalculationInput(
                    date = "2026-10-22",
                    currentHoldings = listOf(freshHolding, staleHolding),
                    yesterdaySnapshotItems = listOf(yestItem1, yestItem2),
                ),
            )

        assertEquals(PnlCompleteness.ESTIMATED, pnl.completeness)
    }

    @Test
    fun `zero and negative quantities are excluded from daily PnL calculation`() {
        val normal =
            createHolding(
                HoldingTestParam(qty = "10", lastPrice = "100.00", prevClose = "90.00"),
            )
        val zero =
            createHolding(
                HoldingTestParam(isin = "INE000000001", qty = "0", lastPrice = "50.00", prevClose = "50.00"),
            )
        val negative =
            createHolding(
                HoldingTestParam(isin = "INE000000002", qty = "-5", lastPrice = "50.00", prevClose = "50.00"),
            )

        val pnl =
            DailyPnlCalculator.calculateDailyPnl(
                DailyPnlCalculator.CalculationInput(
                    date = "2026-10-22",
                    currentHoldings = listOf(normal, zero, negative),
                ),
            )

        assertEquals(Money(BigDecimal("1000.00")), pnl.totalValue)
        assertEquals(Money(BigDecimal("100.00")), pnl.pnlAmount)
    }
}
