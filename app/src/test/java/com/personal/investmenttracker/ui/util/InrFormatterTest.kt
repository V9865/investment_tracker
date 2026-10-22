package com.personal.investmenttracker.ui.util

import com.personal.investmenttracker.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InrFormatterTest {
    @Test
    fun `formats Indian digit grouping correctly`() {
        assertEquals("₹0.00", InrFormatter.formatInr(Money(BigDecimal("0"))))
        assertEquals("₹50.00", InrFormatter.formatInr(Money(BigDecimal("50"))))
        assertEquals("₹500.00", InrFormatter.formatInr(Money(BigDecimal("500"))))
        assertEquals("₹1,000.00", InrFormatter.formatInr(Money(BigDecimal("1000"))))
        assertEquals("₹10,000.00", InrFormatter.formatInr(Money(BigDecimal("10000"))))
        assertEquals("₹1,00,000.00", InrFormatter.formatInr(Money(BigDecimal("100000")))) // 1 Lakh
        assertEquals("₹10,00,000.00", InrFormatter.formatInr(Money(BigDecimal("1000000")))) // 10 Lakhs
        assertEquals("₹1,00,00,000.00", InrFormatter.formatInr(Money(BigDecimal("10000000")))) // 1 Crore
        assertEquals("₹12,34,56,789.50", InrFormatter.formatInr(Money(BigDecimal("123456789.50"))))
    }

    @Test
    fun `formats negative amounts with minus prefix`() {
        assertEquals("-₹1,00,000.00", InrFormatter.formatInr(Money(BigDecimal("-100000"))))
    }

    @Test
    fun `formats positive PnL with sign arrow and text`() {
        val pnl =
            InrFormatter.formatPnl(
                amount = Money(BigDecimal("1500.50")),
                percent = BigDecimal("2.50"),
            )
        assertEquals("+", pnl.sign)
        assertEquals("▲", pnl.arrow)
        assertEquals("+ ▲ ₹1,500.50 (+2.50%)", pnl.displayText)
        assertTrue(pnl.isPositive)
        assertFalse(pnl.isNegative)
        assertEquals("Gain of ₹1,500.50 (2.50 percent)", pnl.accessibilityDescription)
    }

    @Test
    fun `formats negative PnL with sign arrow and text`() {
        val pnl =
            InrFormatter.formatPnl(
                amount = Money(BigDecimal("-750.25")),
                percent = BigDecimal("-1.25"),
            )
        assertEquals("-", pnl.sign)
        assertEquals("▼", pnl.arrow)
        assertEquals("- ▼ ₹750.25 (-1.25%)", pnl.displayText)
        assertTrue(pnl.isNegative)
        assertFalse(pnl.isPositive)
        assertEquals("Loss of ₹750.25 (-1.25 percent)", pnl.accessibilityDescription)
    }

    @Test
    fun `formats zero PnL`() {
        val pnl = InrFormatter.formatPnl(amount = Money.ZERO, percent = BigDecimal.ZERO)
        assertEquals("", pnl.sign)
        assertEquals("—", pnl.arrow)
        assertEquals("— ₹0.00 (0.00%)", pnl.displayText)
        assertTrue(pnl.isZero)
    }

    @Test
    fun `formats null PnL gracefully`() {
        val pnl = InrFormatter.formatPnl(amount = null)
        assertEquals("-", pnl.displayText)
        assertEquals("Not available", pnl.accessibilityDescription)
    }
}
