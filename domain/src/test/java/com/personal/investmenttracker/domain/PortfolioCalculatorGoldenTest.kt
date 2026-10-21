package com.personal.investmenttracker.domain

import com.personal.investmenttracker.domain.calculator.HoldingChange
import com.personal.investmenttracker.domain.calculator.HoldingWarning
import com.personal.investmenttracker.domain.calculator.IntradayBuy
import com.personal.investmenttracker.domain.calculator.PortfolioCalculator
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PortfolioCalculatorGoldenTest {
    /**
     * Worked example 1:
     * 10 sh, prev close 100, close 103 -> daily 30.00 (3.00%).
     */
    @Test
    fun testWorkedExample1_SimpleDailyPnl() {
        val holding =
            Holding(
                broker = Broker.ZERODHA,
                isin = "INE002A01018",
                exchange = Exchange.NSE,
                symbol = "RELIANCE",
                quantity = Quantity.of(10),
                avgPriceReported = Money.of("100.00"),
                quote =
                    Quote(
                        price = Money.of("103.00"),
                        prevClose = Money.of("100.00"),
                        closePrice = Money.of("103.00"),
                        source = QuoteSource.EOD_OFFICIAL,
                        sourceTimestamp = 1700000000L,
                        freshness = Freshness.LIVE,
                    ),
            )

        val calc = PortfolioCalculator.calculateHolding(holding)

        assertEquals("30.00", calc.dailyPnl?.toDisplayString())
        assertEquals("3.00", calc.dailyPnlPercent?.setScale(2, java.math.RoundingMode.HALF_UP)?.toPlainString())
        assertEquals("1030.00", calc.currentValue?.toDisplayString())
        assertEquals("1000.00", calc.costBasis?.toDisplayString())
        assertEquals("30.00", calc.unrealizedPnl?.toDisplayString())
    }

    /**
     * Worked example 2:
     * Held 10 (prev close 100), bought 10 today at 102, close 103 ->
     * 10 x 3 + 10 x 1 = 40.00.
     */
    @Test
    fun testWorkedExample2_CrossCheckMethod() {
        val prevHoldingChange =
            HoldingChange(
                quantityPrev = Quantity.of(10),
                prevClose = Money.of("100.00"),
                close = Money.of("103.00"),
            )
        val intradayBuy =
            IntradayBuy(
                quantityBought = Quantity.of(10),
                buyPrice = Money.of("102.00"),
                close = Money.of("103.00"),
            )

        val dailyPnl =
            PortfolioCalculator.calculateDailyPnlCrossCheckMethod(
                holdingsWithoutTradeChanges = listOf(prevHoldingChange),
                intradayBoughtItems = listOf(intradayBuy),
            )

        assertEquals("40.00", dailyPnl.toDisplayString())
    }

    /**
     * Worked example 3:
     * Snapshot method: yesterday value 1,000; today 1,130 after buying 100 worth today ->
     * 1,130 - 1,000 - 100 = 30.00.
     */
    @Test
    fun testWorkedExample3_SnapshotMethod() {
        val dailyPnl =
            PortfolioCalculator.calculateDailyPnlSnapshotMethod(
                todayValue = Money.of("1130.00"),
                yesterdaySnapshotValue = Money.of("1000.00"),
                netBuysToday = Money.of("100.00"),
                netSellsToday = Money.ZERO,
            )

        assertEquals("30.00", dailyPnl.toDisplayString())
    }

    /**
     * Worked example 4:
     * Same ISIN at both brokers: Zerodha 10 @ 100, Kotak 5 @ 110, price 120 ->
     * combined qty 15, cost 1,550, value 1,800, unrealized 250.00 = 200.00 + 50.00 (no double count).
     */
    @Test
    fun testWorkedExample4_ConsolidatedByIsinNoDoubleCount() {
        val sharedQuote =
            Quote(
                price = Money.of("120.00"),
                prevClose = Money.of("115.00"),
                closePrice = Money.of("120.00"),
                source = QuoteSource.BROKER_LIVE,
                sourceTimestamp = 1700000000L,
                freshness = Freshness.LIVE,
            )

        val zerodhaHolding =
            Holding(
                broker = Broker.ZERODHA,
                isin = "INF209K01164",
                exchange = Exchange.NSE,
                symbol = "NIFTYBEES",
                quantity = Quantity.of(10),
                avgPriceReported = Money.of("100.00"),
                quote = sharedQuote,
            )

        val kotakHolding =
            Holding(
                broker = Broker.KOTAK_NEO,
                isin = "INF209K01164",
                exchange = Exchange.NSE,
                symbol = "NIFTYBEES",
                quantity = Quantity.of(5),
                avgPriceReported = Money.of("110.00"),
                quote = sharedQuote,
            )

        val holdings = listOf(zerodhaHolding, kotakHolding)
        val consolidated = PortfolioCalculator.consolidateByIsin(holdings)

        assertEquals(1, consolidated.size)
        val item = consolidated[0]
        assertEquals("15", item.totalQuantity.toDisplayString())
        assertEquals("1550.00", item.totalCostBasis?.toDisplayString())
        assertEquals("1800.00", item.totalCurrentValue?.toDisplayString())
        assertEquals("250.00", item.unrealizedPnl?.toDisplayString())

        // Verify broker subtotals and portfolio roll-up
        val summary = PortfolioCalculator.calculatePortfolio(holdings)
        assertEquals("1550.00", summary.totalCostBasis.toDisplayString())
        assertEquals("1800.00", summary.totalCurrentValue.toDisplayString())
        assertEquals("250.00", summary.unrealizedPnl.toDisplayString())

        val zerodhaSubtotal = summary.brokerSubtotals[Broker.ZERODHA]!!
        val kotakSubtotal = summary.brokerSubtotals[Broker.KOTAK_NEO]!!

        // Zerodha: 10 * 120 = 1200, cost 1000, unrealized 200.00
        assertEquals("1000.00", zerodhaSubtotal.totalCostBasis.toDisplayString())
        assertEquals("1200.00", zerodhaSubtotal.totalCurrentValue.toDisplayString())
        assertEquals("200.00", zerodhaSubtotal.unrealizedPnl.toDisplayString())

        // Kotak: 5 * 120 = 600, cost 550, unrealized 50.00
        assertEquals("550.00", kotakSubtotal.totalCostBasis.toDisplayString())
        assertEquals("600.00", kotakSubtotal.totalCurrentValue.toDisplayString())
        assertEquals("50.00", kotakSubtotal.unrealizedPnl.toDisplayString())
    }

    /**
     * Edge case 1: Zero quantity is ignored.
     */
    @Test
    fun testEdgeCase_ZeroQuantityIgnored() {
        val zeroHolding =
            Holding(
                broker = Broker.ZERODHA,
                isin = "INE009A01021",
                exchange = Exchange.NSE,
                symbol = "INFY",
                quantity = Quantity.ZERO,
                avgPriceReported = Money.of("1400.00"),
                quote =
                    Quote(
                        price = Money.of("1500.00"),
                        prevClose = Money.of("1450.00"),
                        source = QuoteSource.BROKER_LIVE,
                        sourceTimestamp = 1700000000L,
                        freshness = Freshness.LIVE,
                    ),
            )

        val calc = PortfolioCalculator.calculateHolding(zeroHolding)
        assertTrue(calc.warnings.contains(HoldingWarning.ZERO_QUANTITY_IGNORED))
        assertEquals(Money.ZERO, calc.currentValue)

        val consolidated = PortfolioCalculator.consolidateByIsin(listOf(zeroHolding))
        assertTrue("Zero quantity should be omitted from active consolidated holdings", consolidated.isEmpty())
    }

    /**
     * Edge case 2: Null average price -> cost unknown, excluded from % and flagged incomplete.
     */
    @Test
    fun testEdgeCase_NullAveragePriceFlagged() {
        val holding =
            Holding(
                broker = Broker.KOTAK_NEO,
                isin = "INE009A01021",
                exchange = Exchange.NSE,
                symbol = "INFY",
                quantity = Quantity.of(10),
                avgPriceReported = null,
                quote =
                    Quote(
                        price = Money.of("1500.00"),
                        prevClose = Money.of("1480.00"),
                        source = QuoteSource.BROKER_LIVE,
                        sourceTimestamp = 1700000000L,
                        freshness = Freshness.LIVE,
                    ),
            )

        val calc = PortfolioCalculator.calculateHolding(holding)
        assertTrue(calc.warnings.contains(HoldingWarning.COST_UNKNOWN_INCOMPLETE))
        assertNull(calc.costBasis)
        assertNull(calc.unrealizedPnl)
        assertNull(calc.unrealizedPnlPercent)
        assertEquals("15000.00", calc.currentValue?.toDisplayString())
        // Daily P&L still functions normally based on market quotes
        assertEquals("200.00", calc.dailyPnl?.toDisplayString())
    }

    /**
     * Edge case 3: Negative quantity = anomaly, excluded with warning.
     */
    @Test
    fun testEdgeCase_NegativeQuantityAnomaly() {
        val anomalousHolding =
            Holding(
                broker = Broker.ZERODHA,
                isin = "INE002A01018",
                exchange = Exchange.NSE,
                symbol = "RELIANCE",
                quantity = Quantity(BigDecimal("-5")),
                avgPriceReported = Money.of("2000.00"),
                quote =
                    Quote(
                        price = Money.of("2100.00"),
                        prevClose = Money.of("2050.00"),
                        source = QuoteSource.BROKER_LIVE,
                        sourceTimestamp = 1700000000L,
                        freshness = Freshness.LIVE,
                    ),
            )

        val calc = PortfolioCalculator.calculateHolding(anomalousHolding)
        assertTrue(calc.warnings.contains(HoldingWarning.NEGATIVE_QUANTITY_ANOMALY))
        assertNull(calc.currentValue)
        assertNull(calc.costBasis)
        assertNull(calc.dailyPnl)

        val summary = PortfolioCalculator.calculatePortfolio(listOf(anomalousHolding))
        assertEquals(1, summary.anomalousHoldings.size)
        assertEquals(Money.ZERO, summary.totalCurrentValue)
    }

    /**
     * Edge case 4: Stale quote = value shown with STALE badge, excluded from daily P&L.
     */
    @Test
    fun testEdgeCase_StaleQuoteExcludedFromDailyPnl() {
        val staleHolding =
            Holding(
                broker = Broker.ZERODHA,
                isin = "INE040A01034",
                exchange = Exchange.NSE,
                symbol = "HDFCBANK",
                quantity = Quantity.of(10),
                avgPriceReported = Money.of("1600.00"),
                quote =
                    Quote(
                        price = Money.of("1650.00"),
                        prevClose = Money.of("1620.00"),
                        source = QuoteSource.BROKER_CACHE,
                        sourceTimestamp = 1600000000L,
                        freshness = Freshness.STALE,
                    ),
            )

        val calc = PortfolioCalculator.calculateHolding(staleHolding)
        assertTrue(calc.warnings.contains(HoldingWarning.STALE_QUOTE))
        // Current value still shown with STALE badge
        assertEquals("16500.00", calc.currentValue?.toDisplayString())
        // But excluded from daily P&L
        assertNull(calc.dailyPnl)
        assertNull(calc.dailyPnlPercent)
    }
}
