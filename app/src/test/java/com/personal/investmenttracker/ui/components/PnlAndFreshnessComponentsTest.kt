package com.personal.investmenttracker.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Money
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PnlAndFreshnessComponentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testPnlTextBadgePositive() {
        composeTestRule.setContent {
            PnlTextBadge(
                amount = Money(BigDecimal("1250.50")),
                percent = BigDecimal("3.25"),
            )
        }
        composeTestRule.onNodeWithText("+ ▲ ₹1,250.50 (+3.25%)").assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(
                "Gain of ₹1,250.50 (3.25 percent)",
            ).assertIsDisplayed()
    }

    @Test
    fun testPnlTextBadgeNegative() {
        composeTestRule.setContent {
            PnlTextBadge(
                amount = Money(BigDecimal("-450.00")),
                percent = BigDecimal("-1.50"),
            )
        }
        composeTestRule.onNodeWithText("- ▼ ₹450.00 (-1.50%)").assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(
                "Loss of ₹450.00 (-1.50 percent)",
            ).assertIsDisplayed()
    }

    @Test
    fun testPnlTextBadgeZero() {
        composeTestRule.setContent {
            PnlTextBadge(
                amount = Money.ZERO,
                percent = BigDecimal.ZERO,
            )
        }
        composeTestRule.onNodeWithText("— ₹0.00 (0.00%)").assertIsDisplayed()
    }

    @Test
    fun testPnlTextBadgeNull() {
        composeTestRule.setContent {
            PnlTextBadge(amount = null)
        }
        composeTestRule.onNodeWithText("-").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Not available").assertIsDisplayed()
    }

    @Test
    fun testFreshnessChipLiveAndStale() {
        val quote =
            com.personal.investmenttracker.domain.model.Quote(
                price = Money(BigDecimal("1500.00")),
                prevClose = Money(BigDecimal("1480.00")),
                source = com.personal.investmenttracker.domain.model.QuoteSource.BROKER_LIVE,
                sourceTimestamp = 0L,
                freshness = Freshness.LIVE,
            )
        composeTestRule.setContent {
            FreshnessChip(quote = quote)
        }
        composeTestRule.onNodeWithText("LIVE").assertIsDisplayed()
    }

    @Test
    fun testFreshnessChipMissingQuote() {
        composeTestRule.setContent {
            FreshnessChip(quote = null)
        }
        composeTestRule
            .onNodeWithText(
                "Not available from current data source",
            ).assertIsDisplayed()
    }

    @Test
    fun testFreshnessChipEod() {
        val quote =
            com.personal.investmenttracker.domain.model.Quote(
                price = Money(BigDecimal("1500.00")),
                prevClose = Money(BigDecimal("1480.00")),
                source = com.personal.investmenttracker.domain.model.QuoteSource.EOD_OFFICIAL,
                sourceTimestamp = 0L,
                freshness = Freshness.EOD,
            )
        composeTestRule.setContent {
            FreshnessChip(quote = quote)
        }
        composeTestRule.onNodeWithText("EOD").assertIsDisplayed()
    }
}
