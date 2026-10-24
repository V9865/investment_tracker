package com.personal.investmenttracker.domain.calculator

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.ListingKey
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import java.math.BigDecimal

enum class HoldingWarning {
    ZERO_QUANTITY_IGNORED,
    NEGATIVE_QUANTITY_ANOMALY,
    COST_UNKNOWN_INCOMPLETE,
    STALE_QUOTE,
    MISSING_QUOTE,
}

data class HoldingCalculation(
    val holding: Holding,
    val currentValue: Money?,
    val costBasis: Money?,
    val unrealizedPnl: Money?,
    val unrealizedPnlPercent: BigDecimal?,
    val dailyPnl: Money?,
    val dailyPnlPercent: BigDecimal?,
    val warnings: Set<HoldingWarning> = emptySet(),
)

data class ConsolidatedHolding(
    val listingKey: ListingKey,
    val symbol: String,
    val totalQuantity: Quantity,
    val totalCostBasis: Money?,
    val totalCurrentValue: Money?,
    val unrealizedPnl: Money?,
    val unrealizedPnlPercent: BigDecimal?,
    val dailyPnl: Money?,
    val dailyPnlPercent: BigDecimal?,
    val brokerHoldings: List<Holding>,
    val warnings: Set<HoldingWarning> = emptySet(),
)

data class BrokerSubtotal(
    val broker: Broker,
    val totalCostBasis: Money,
    val totalCurrentValue: Money,
    val unrealizedPnl: Money,
    val unrealizedPnlPercent: BigDecimal?,
    val dailyPnl: Money,
    val activeHoldingsCount: Int,
    val incompleteHoldingsCount: Int,
    val hasStaleQuotes: Boolean,
)

data class PortfolioSummary(
    val totalCostBasis: Money,
    val totalCurrentValue: Money,
    val unrealizedPnl: Money,
    val unrealizedPnlPercent: BigDecimal?,
    val dailyPnl: Money,
    val brokerSubtotals: Map<Broker, BrokerSubtotal> = emptyMap(),
    val consolidatedHoldings: List<ConsolidatedHolding> = emptyList(),
    val anomalousHoldings: List<Holding> = emptyList(),
)

object PortfolioCalculator {
    fun calculateHolding(holding: Holding): HoldingCalculation =
        when {
            holding.isNegativeQty -> makeAnomalousHolding(holding)
            holding.isZeroQty -> makeZeroQtyHolding(holding)
            else -> computeActiveHolding(holding)
        }

    private fun makeAnomalousHolding(holding: Holding): HoldingCalculation =
        HoldingCalculation(
            holding = holding,
            currentValue = null,
            costBasis = null,
            unrealizedPnl = null,
            unrealizedPnlPercent = null,
            dailyPnl = null,
            dailyPnlPercent = null,
            warnings = setOf(HoldingWarning.NEGATIVE_QUANTITY_ANOMALY),
        )

    private fun makeZeroQtyHolding(holding: Holding): HoldingCalculation =
        HoldingCalculation(
            holding = holding,
            currentValue = Money.ZERO,
            costBasis = Money.ZERO,
            unrealizedPnl = Money.ZERO,
            unrealizedPnlPercent = null,
            dailyPnl = Money.ZERO,
            dailyPnlPercent = null,
            warnings = setOf(HoldingWarning.ZERO_QUANTITY_IGNORED),
        )

    private fun computeActiveHolding(holding: Holding): HoldingCalculation {
        val warnings = mutableSetOf<HoldingWarning>()
        val quote = holding.quote
        when {
            quote == null -> warnings.add(HoldingWarning.MISSING_QUOTE)
            quote.isStale -> warnings.add(HoldingWarning.STALE_QUOTE)
        }

        val costBasis =
            if (holding.isCostUnknown) {
                warnings.add(HoldingWarning.COST_UNKNOWN_INCOMPLETE)
                null
            } else {
                holding.quantity * holding.avgPriceReported!!
            }

        val effectivePrice = quote?.price ?: quote?.closePrice
        val currentValue = effectivePrice?.let { holding.quantity * it }
        val unrealizedPnl = PnlMath.computeUnrealized(currentValue, costBasis)
        val unrealizedPnlPercent = PnlMath.computeUnrealizedPct(unrealizedPnl, costBasis)
        val dailyPnl = PnlMath.computeDailyPnl(holding.quantity, quote)
        val dailyPnlPercent = PnlMath.computeDailyPnlPct(quote)

        return HoldingCalculation(
            holding = holding,
            currentValue = currentValue,
            costBasis = costBasis,
            unrealizedPnl = unrealizedPnl,
            unrealizedPnlPercent = unrealizedPnlPercent,
            dailyPnl = dailyPnl,
            dailyPnlPercent = dailyPnlPercent,
            warnings = warnings,
        )
    }

    fun consolidateByIsin(holdings: List<Holding>): List<ConsolidatedHolding> {
        val validHoldings = holdings.filter { !it.isNegativeQty && !it.isZeroQty }
        val grouped = validHoldings.groupBy { it.listingKey }
        return grouped.map { (key, groupHoldings) -> buildConsolidatedItem(key, groupHoldings) }
    }

    private fun buildConsolidatedItem(
        key: ListingKey,
        groupHoldings: List<Holding>,
    ): ConsolidatedHolding {
        val symbol = groupHoldings.first().symbol
        val totalQty = groupHoldings.fold(Quantity.ZERO) { acc, h -> acc + h.quantity }
        val hasCostUnknown = groupHoldings.any { it.isCostUnknown }

        val totalCostBasis =
            if (hasCostUnknown) {
                null
            } else {
                groupHoldings.fold(Money.ZERO) { acc, h -> acc + (h.quantity * h.avgPriceReported!!) }
            }

        val quote = groupHoldings.mapNotNull { it.quote }.firstOrNull()
        val effectivePrice = quote?.price ?: quote?.closePrice
        val totalCurrentValue = effectivePrice?.let { totalQty * it }
        val unrealizedPnl = PnlMath.computeUnrealized(totalCurrentValue, totalCostBasis)
        val unrealizedPercent = PnlMath.computeUnrealizedPct(unrealizedPnl, totalCostBasis)
        val dailyPnl = PnlMath.computeDailyPnl(totalQty, quote)
        val dailyPercent = PnlMath.computeDailyPnlPct(quote)

        val warnings = mutableSetOf<HoldingWarning>()
        if (hasCostUnknown) warnings.add(HoldingWarning.COST_UNKNOWN_INCOMPLETE)
        if (quote == null) warnings.add(HoldingWarning.MISSING_QUOTE)
        if (quote != null && quote.isStale) warnings.add(HoldingWarning.STALE_QUOTE)

        return ConsolidatedHolding(
            listingKey = key,
            symbol = symbol,
            totalQuantity = totalQty,
            totalCostBasis = totalCostBasis,
            totalCurrentValue = totalCurrentValue,
            unrealizedPnl = unrealizedPnl,
            unrealizedPnlPercent = unrealizedPercent,
            dailyPnl = dailyPnl,
            dailyPnlPercent = dailyPercent,
            brokerHoldings = groupHoldings,
            warnings = warnings,
        )
    }

    fun calculatePortfolio(holdings: List<Holding>): PortfolioSummary {
        val calcList = holdings.map { calculateHolding(it) }
        val anomalous = holdings.filter { it.isNegativeQty }

        val subtotalsByBroker =
            Broker.values().associateWith { broker ->
                buildBrokerSubtotal(broker, calcList)
            }

        val totalCost = subtotalsByBroker.values.fold(Money.ZERO) { acc, s -> acc + s.totalCostBasis }
        val totalValue = subtotalsByBroker.values.fold(Money.ZERO) { acc, s -> acc + s.totalCurrentValue }
        val totalUnrealized = totalValue - totalCost
        val totalUnrealizedPct = PnlMath.computeUnrealizedPct(totalUnrealized, totalCost)
        val totalDaily = subtotalsByBroker.values.fold(Money.ZERO) { acc, s -> acc + s.dailyPnl }

        return PortfolioSummary(
            totalCostBasis = totalCost,
            totalCurrentValue = totalValue,
            unrealizedPnl = totalUnrealized,
            unrealizedPnlPercent = totalUnrealizedPct,
            dailyPnl = totalDaily,
            brokerSubtotals = subtotalsByBroker,
            consolidatedHoldings = consolidateByIsin(holdings),
            anomalousHoldings = anomalous,
        )
    }

    private fun buildBrokerSubtotal(
        broker: Broker,
        calcList: List<HoldingCalculation>,
    ): BrokerSubtotal {
        val brokerCalcs =
            calcList.filter {
                it.holding.broker == broker &&
                    !it.warnings.contains(HoldingWarning.NEGATIVE_QUANTITY_ANOMALY) &&
                    !it.warnings.contains(HoldingWarning.ZERO_QUANTITY_IGNORED)
            }

        val cost = brokerCalcs.mapNotNull { it.costBasis }.fold(Money.ZERO) { acc, m -> acc + m }
        val value = brokerCalcs.mapNotNull { it.currentValue }.fold(Money.ZERO) { acc, m -> acc + m }
        val unrealized = value - cost
        val unrealizedPct = PnlMath.computeUnrealizedPct(unrealized, cost)
        val daily = brokerCalcs.mapNotNull { it.dailyPnl }.fold(Money.ZERO) { acc, m -> acc + m }

        return BrokerSubtotal(
            broker = broker,
            totalCostBasis = cost,
            totalCurrentValue = value,
            unrealizedPnl = unrealized,
            unrealizedPnlPercent = unrealizedPct,
            dailyPnl = daily,
            activeHoldingsCount = brokerCalcs.size,
            incompleteHoldingsCount =
                brokerCalcs.count {
                    it.warnings.contains(HoldingWarning.COST_UNKNOWN_INCOMPLETE)
                },
            hasStaleQuotes = brokerCalcs.any { it.warnings.contains(HoldingWarning.STALE_QUOTE) },
        )
    }

    fun calculateDailyPnlSnapshotMethod(
        todayValue: Money,
        yesterdaySnapshotValue: Money,
        netBuysToday: Money,
        netSellsToday: Money,
    ): Money = todayValue - yesterdaySnapshotValue - netBuysToday + netSellsToday

    fun calculateDailyPnlCrossCheckMethod(
        holdingsWithoutTradeChanges: List<HoldingChange>,
        intradayBoughtItems: List<IntradayBuy> = emptyList(),
    ): Money {
        val existingDailyPnl =
            holdingsWithoutTradeChanges.fold(Money.ZERO) { acc, item ->
                val priceDiff = item.close - item.prevClose
                acc + (item.quantityPrev * priceDiff)
            }

        val intradayDailyPnl =
            intradayBoughtItems.fold(Money.ZERO) { acc, item ->
                val gainOnBuy = item.close - item.buyPrice
                acc + (item.quantityBought * gainOnBuy)
            }

        return existingDailyPnl + intradayDailyPnl
    }
}

data class HoldingChange(
    val quantityPrev: Quantity,
    val prevClose: Money,
    val close: Money,
)

data class IntradayBuy(
    val quantityBought: Quantity,
    val buyPrice: Money,
    val close: Money,
)
