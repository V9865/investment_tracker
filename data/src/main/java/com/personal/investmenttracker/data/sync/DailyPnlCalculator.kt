package com.personal.investmenttracker.data.sync

import com.personal.investmenttracker.domain.model.DailyPnl
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.ListingKey
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.PnlCompleteness
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.SnapshotItem
import java.math.BigDecimal

object DailyPnlCalculator {
    data class CalculationInput(
        val date: String,
        val currentHoldings: List<Holding>,
        val yesterdaySnapshotItems: List<SnapshotItem> = emptyList(),
    )

    fun calculateDailyPnl(input: CalculationInput): DailyPnl {
        val validHoldings = input.currentHoldings.filter { !it.isZeroQty && !it.isNegativeQty }
        val completeness = determineCompleteness(validHoldings, input.yesterdaySnapshotItems)
        val todayTotalValue = computeTodayTotalValue(validHoldings)

        if (input.yesterdaySnapshotItems.isEmpty()) {
            return DailyPnl(
                date = input.date,
                totalValue = todayTotalValue,
                pnlAmount = computeEstimatedPnlFromPrevClose(validHoldings),
                netTradeCashFlow = Money.ZERO,
                completeness = PnlCompleteness.ESTIMATED,
            )
        }

        val yesterdayTotalValue =
            input.yesterdaySnapshotItems.fold(Money.ZERO) { acc, item ->
                acc + item.value
            }

        val netTradeCashFlow = computeNetTradeCashFlow(validHoldings, input.yesterdaySnapshotItems)
        val pnlAmount = todayTotalValue - yesterdayTotalValue - netTradeCashFlow

        return DailyPnl(
            date = input.date,
            totalValue = todayTotalValue,
            pnlAmount = pnlAmount,
            netTradeCashFlow = netTradeCashFlow,
            completeness = completeness,
        )
    }

    private fun determineCompleteness(
        validHoldings: List<Holding>,
        yesterdayItems: List<SnapshotItem>,
    ): PnlCompleteness {
        val hasMissingOrStaleQuote = validHoldings.any { it.quote == null || it.quote?.isStale == true }
        return if (yesterdayItems.isEmpty() || hasMissingOrStaleQuote) {
            PnlCompleteness.ESTIMATED
        } else {
            PnlCompleteness.COMPLETE
        }
    }

    private fun computeTodayTotalValue(validHoldings: List<Holding>): Money =
        validHoldings.fold(Money.ZERO) { acc, holding ->
            val quote = holding.quote
            if (quote != null && !quote.isStale) {
                val price = quote.closePrice ?: quote.price
                acc + (holding.quantity * price)
            } else {
                acc
            }
        }

    private fun computeEstimatedPnlFromPrevClose(validHoldings: List<Holding>): Money =
        validHoldings.fold(Money.ZERO) { acc, holding ->
            val quote = holding.quote
            if (quote != null && !quote.isStale) {
                val price = quote.closePrice ?: quote.price
                acc + (holding.quantity * (price - quote.prevClose))
            } else {
                acc
            }
        }

    private fun computeNetTradeCashFlow(
        validHoldings: List<Holding>,
        yesterdayItems: List<SnapshotItem>,
    ): Money {
        val todayGrouped = validHoldings.groupBy { ListingKey(it.isin, it.exchange) }
        val yesterdayGrouped = yesterdayItems.groupBy { ListingKey(it.isin, it.exchange) }
        val allKeys = todayGrouped.keys + yesterdayGrouped.keys

        var netBuys = Money.ZERO
        var netSells = Money.ZERO

        for (key in allKeys) {
            val todayList = todayGrouped[key].orEmpty()
            val yestList = yesterdayGrouped[key].orEmpty()

            val qtyToday = todayList.fold(Quantity.ZERO) { acc, h -> acc + h.quantity }
            val qtyPrev = yestList.fold(Quantity.ZERO) { acc, s -> acc + s.quantity }

            val representative = todayList.firstOrNull()
            val quote = representative?.quote
            val currentPrice = quote?.let { it.closePrice ?: it.price }

            if (qtyToday.value > qtyPrev.value) {
                val qtyBought = Quantity(qtyToday.value.subtract(qtyPrev.value))
                val buyPrice = representative?.avgPriceReported ?: currentPrice ?: Money.ZERO
                netBuys += qtyBought * buyPrice
            } else if (qtyToday.value < qtyPrev.value) {
                val qtySold = Quantity(qtyPrev.value.subtract(qtyToday.value))
                val sellPrice = currentPrice ?: yestList.firstOrNull()?.closePrice ?: Money.ZERO
                netSells += qtySold * sellPrice
            }
        }

        return netBuys - netSells
    }

    /**
     * Section 5 Cross-check formula:
     * sum(qty_prev * (close - prev_close)) + sum(qty_bought_today * (close - buy_price))
     */
    fun crossCheckDailyPnl(
        validHoldings: List<Holding>,
        yesterdaySnapshotItems: List<SnapshotItem>,
    ): Money {
        val todayGrouped = validHoldings.groupBy { ListingKey(it.isin, it.exchange) }
        val yesterdayGrouped = yesterdaySnapshotItems.groupBy { ListingKey(it.isin, it.exchange) }
        val allKeys = todayGrouped.keys + yesterdayGrouped.keys

        var totalCrossCheck = Money.ZERO
        for (key in allKeys) {
            totalCrossCheck +=
                computeKeyCrossCheck(
                    todayGrouped[key].orEmpty(),
                    yesterdayGrouped[key].orEmpty(),
                )
        }
        return totalCrossCheck
    }

    private fun computeKeyCrossCheck(
        todayItems: List<Holding>,
        yestItems: List<SnapshotItem>,
    ): Money {
        val quote = todayItems.firstOrNull()?.quote
        if (quote == null || quote.isStale) return Money.ZERO

        val closePrice = quote.closePrice ?: quote.price
        val qtyToday = todayItems.fold(Quantity.ZERO) { acc, h -> acc + h.quantity }
        val qtyPrev = yestItems.fold(Quantity.ZERO) { acc, s -> acc + s.quantity }

        var keyTotal = Money.ZERO
        if (qtyPrev.value.compareTo(BigDecimal.ZERO) > 0) {
            val heldQty = if (qtyToday.value < qtyPrev.value) qtyToday else qtyPrev
            keyTotal += heldQty * (closePrice - quote.prevClose)
        }

        if (qtyToday.value > qtyPrev.value) {
            val qtyBought = Quantity(qtyToday.value.subtract(qtyPrev.value))
            val buyPrice = todayItems.firstOrNull()?.avgPriceReported ?: closePrice
            keyTotal += qtyBought * (closePrice - buyPrice)
        }
        return keyTotal
    }
}
