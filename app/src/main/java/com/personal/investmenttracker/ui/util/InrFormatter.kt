package com.personal.investmenttracker.ui.util

import com.personal.investmenttracker.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

object InrFormatter {
    /**
     * Formats a BigDecimal or Money into Indian numbering system grouping.
     * Example:
     * 100 -> "100.00"
     * 1000 -> "1,000.00"
     * 100000 -> "1,00,000.00"
     * 10000000 -> "1,00,00,000.00"
     */
    fun formatInr(
        money: Money,
        showSymbol: Boolean = true,
        scale: Int = 2,
    ): String = formatInrAmount(money.amount, showSymbol, scale)

    fun formatInrAmount(
        amount: BigDecimal,
        showSymbol: Boolean = true,
        scale: Int = 2,
    ): String {
        val isNegative = amount.signum() < 0
        val absAmount = amount.abs().setScale(scale, RoundingMode.HALF_UP)
        val plainString = absAmount.toPlainString()

        val parts = plainString.split(".")
        val integerPart = parts[0]
        val fractionalPart = if (parts.size > 1) "." + parts[1] else ""

        val formattedInteger = formatIndianGrouping(integerPart)
        val symbolPrefix = if (showSymbol) "₹" else ""
        val sign = if (isNegative) "-" else ""

        return "$sign$symbolPrefix$formattedInteger$fractionalPart"
    }

    private fun formatIndianGrouping(digits: String): String {
        if (digits.length <= 3) return digits

        // Last 3 digits
        val lastThree = digits.substring(digits.length - 3)
        val remaining = digits.substring(0, digits.length - 3)

        // Group the remaining digits into 2s from right to left
        val sb = java.lang.StringBuilder()
        var count = 0
        for (i in remaining.length - 1 downTo 0) {
            sb.append(remaining[i])
            count++
            if (count % 2 == 0 && i > 0) {
                sb.append(',')
            }
        }
        val groupedRemaining = sb.reverse().toString()
        return "$groupedRemaining,$lastThree"
    }

    /**
     * Formats P&L with sign + arrow + text for visual and TalkBack accessibility.
     * Returns a structured display model so that colour alone is never used.
     */
    data class PnlDisplay(
        val sign: String,
        val arrow: String,
        val formattedAmount: String,
        val formattedPercent: String?,
        val displayText: String,
        val accessibilityDescription: String,
        val isPositive: Boolean,
        val isNegative: Boolean,
        val isZero: Boolean,
    )

    fun formatPnl(
        amount: Money?,
        percent: BigDecimal? = null,
        showSymbol: Boolean = true,
    ): PnlDisplay {
        if (amount == null) {
            return emptyPnlDisplay()
        }

        val cmp = amount.amount.compareTo(BigDecimal.ZERO)
        val isPositive = cmp > 0
        val isNegative = cmp < 0
        val isZero = cmp == 0

        val sign = resolveSign(isPositive, isNegative)
        val arrow = resolveArrow(isPositive, isNegative)
        val formattedAmt = formatInr(Money(amount.amount.abs()), showSymbol = showSymbol)
        val formattedPct = formatPercent(percent, sign)

        val displayText = buildDisplayText(sign, arrow, formattedAmt, formattedPct)
        val accessibilityDesc = buildAccessibilityDesc(amount, percent, isPositive, isNegative)

        return PnlDisplay(
            sign = sign,
            arrow = arrow,
            formattedAmount = formattedAmt,
            formattedPercent = formattedPct,
            displayText = displayText,
            accessibilityDescription = accessibilityDesc,
            isPositive = isPositive,
            isNegative = isNegative,
            isZero = isZero,
        )
    }

    private fun emptyPnlDisplay() =
        PnlDisplay(
            sign = "",
            arrow = "",
            formattedAmount = "-",
            formattedPercent = null,
            displayText = "-",
            accessibilityDescription = "Not available",
            isPositive = false,
            isNegative = false,
            isZero = true,
        )

    private fun resolveSign(
        isPositive: Boolean,
        isNegative: Boolean,
    ): String =
        when {
            isPositive -> "+"
            isNegative -> "-"
            else -> ""
        }

    private fun resolveArrow(
        isPositive: Boolean,
        isNegative: Boolean,
    ): String =
        when {
            isPositive -> "▲"
            isNegative -> "▼"
            else -> "—"
        }

    private fun formatPercent(
        percent: BigDecimal?,
        sign: String,
    ): String? =
        percent?.let {
            val absPct = it.abs().setScale(2, RoundingMode.HALF_UP).toPlainString()
            "($sign$absPct%)"
        }

    private fun buildDisplayText(
        sign: String,
        arrow: String,
        formattedAmt: String,
        formattedPct: String?,
    ): String =
        buildString {
            if (sign.isNotEmpty()) append("$sign ")
            append("$arrow $formattedAmt")
            if (formattedPct != null) append(" $formattedPct")
        }

    private fun buildAccessibilityDesc(
        amount: Money,
        percent: BigDecimal?,
        isPositive: Boolean,
        isNegative: Boolean,
    ): String =
        buildString {
            when {
                isPositive -> append("Gain of ")
                isNegative -> append("Loss of ")
                else -> append("Zero change, ")
            }
            append(formatInr(Money(amount.amount.abs()), showSymbol = true))
            if (percent != null) {
                val pctStr = percent.setScale(2, RoundingMode.HALF_UP).toPlainString()
                append(" ($pctStr percent)")
            }
        }
}
