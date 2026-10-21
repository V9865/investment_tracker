package com.personal.investmenttracker.domain.calculator

import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import java.math.BigDecimal
import java.math.RoundingMode

internal object PnlMath {
    private const val PERCENT_SCALE = 6
    private const val MULTIPLIER_100 = "100"

    fun computeUnrealized(
        currentValue: Money?,
        costBasis: Money?,
    ): Money? = if (currentValue != null && costBasis != null) currentValue - costBasis else null

    fun computeUnrealizedPct(
        unrealized: Money?,
        cost: Money?,
    ): BigDecimal? {
        if (unrealized == null || cost == null || cost.amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null
        }
        return unrealized.amount
            .divide(cost.amount, PERCENT_SCALE, RoundingMode.HALF_UP)
            .multiply(BigDecimal(MULTIPLIER_100))
    }

    fun computeDailyPnl(
        qty: Quantity,
        quote: Quote?,
    ): Money? {
        if (quote == null || quote.isStale) return null
        val priceForDay = quote.closePrice ?: quote.price
        return qty * (priceForDay - quote.prevClose)
    }

    fun computeDailyPnlPct(quote: Quote?): BigDecimal? {
        if (quote == null || quote.isStale || quote.prevClose.amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null
        }
        val priceForDay = quote.closePrice ?: quote.price
        val priceDiff = priceForDay - quote.prevClose
        return priceDiff.amount
            .divide(quote.prevClose.amount, PERCENT_SCALE, RoundingMode.HALF_UP)
            .multiply(BigDecimal(MULTIPLIER_100))
    }
}
