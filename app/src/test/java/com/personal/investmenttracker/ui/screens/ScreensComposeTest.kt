package com.personal.investmenttracker.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.personal.investmenttracker.domain.calculator.ConsolidatedHolding
import com.personal.investmenttracker.domain.calculator.PortfolioSummary
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.DailyPnl
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.ListingKey
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.PnlCompleteness
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.ui.dashboard.BrokerCardUiModel
import com.personal.investmenttracker.ui.dashboard.DashboardScreen
import com.personal.investmenttracker.ui.dashboard.DashboardUiState
import com.personal.investmenttracker.ui.history.PnlHistoryScreen
import com.personal.investmenttracker.ui.history.PnlHistoryUiState
import com.personal.investmenttracker.ui.holdings.HoldingsScreen
import com.personal.investmenttracker.ui.holdings.HoldingsUiState
import com.personal.investmenttracker.ui.holdings.HoldingsViewMode
import com.personal.investmenttracker.ui.settings.PrivacyScreen
import com.personal.investmenttracker.ui.settings.SettingsActions
import com.personal.investmenttracker.ui.settings.SettingsScreen
import com.personal.investmenttracker.ui.settings.SettingsUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreensComposeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testDashboardScreenDisplaysMetrics() {
        var synced = false
        var locked = false
        val summary =
            PortfolioSummary(
                totalCurrentValue = Money(BigDecimal("250000.00")),
                totalCostBasis = Money(BigDecimal("200000.00")),
                unrealizedPnl = Money(BigDecimal("50000.00")),
                unrealizedPnlPercent = BigDecimal("25.00"),
                dailyPnl = Money(BigDecimal("3500.00")),
            )
        val brokerCards =
            listOf(
                BrokerCardUiModel(
                    broker = Broker.ZERODHA,
                    subtotal = null,
                    lastSyncAt = 1761132600000L,
                    status = "CONNECTED",
                ),
                BrokerCardUiModel(
                    broker = Broker.KOTAK_NEO,
                    subtotal = null,
                    lastSyncAt = 1761132600000L,
                    status = "STALE",
                    sessionExpired = true,
                    bannerMessage = "Kotak Neo session expired. Please re-login.",
                ),
            )

        composeTestRule.setContent {
            DashboardScreen(
                state =
                    DashboardUiState(
                        isLoading = false,
                        portfolioSummary = summary,
                        brokerCards = brokerCards,
                    ),
                onSyncClick = { synced = true },
                onLockClick = { locked = true },
                onReconnectBroker = {},
            )
        }

        composeTestRule.onNodeWithText("Total Portfolio Value").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹2,50,000.00").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Kotak Neo session expired. Please re-login.",
            ).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Sync Portfolios").performClick()
        assertTrue(synced)

        composeTestRule.onNodeWithContentDescription("Lock App").performClick()
        assertTrue(locked)
    }

    @Test
    fun testHoldingsScreenConsolidatedToggle() {
        var modeSelected = false
        val sampleHolding =
            ConsolidatedHolding(
                listingKey = ListingKey("INE002A01018", Exchange.NSE),
                symbol = "RELIANCE",
                totalQuantity = Quantity(BigDecimal("10")),
                totalCostBasis = Money(BigDecimal("25000.00")),
                totalCurrentValue = Money(BigDecimal("26000.00")),
                unrealizedPnl = Money(BigDecimal("1000.00")),
                unrealizedPnlPercent = BigDecimal("4.00"),
                dailyPnl = Money(BigDecimal("250.00")),
                dailyPnlPercent = BigDecimal("0.97"),
                brokerHoldings = emptyList(),
            )

        composeTestRule.setContent {
            HoldingsScreen(
                state =
                    HoldingsUiState(
                        isLoading = false,
                        selectedViewMode = HoldingsViewMode.CONSOLIDATED,
                        consolidatedHoldings = listOf(sampleHolding),
                    ),
                onViewModeSelected = { modeSelected = true },
                onSortSelected = {},
            )
        }

        composeTestRule.onNodeWithText("RELIANCE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zerodha").performClick()
        assertTrue(modeSelected)
    }

    @Test
    fun testPnlHistoryScreen() {
        val history =
            listOf(
                DailyPnl(
                    date = "2026-10-21",
                    totalValue = Money(BigDecimal("150000.00")),
                    netTradeCashFlow = Money(BigDecimal("0")),
                    pnlAmount = Money(BigDecimal("1200.00")),
                    completeness = PnlCompleteness.COMPLETE,
                ),
            )

        composeTestRule.setContent {
            PnlHistoryScreen(
                state = PnlHistoryUiState(isLoading = false, historyItems = history),
            )
        }

        composeTestRule.onNodeWithText("2026-10-21").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹1,50,000.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("COMPLETE").assertIsDisplayed()
    }

    @Test
    fun testSettingsScreenSeparateActions() {
        var locked = false
        var erased = false
        var disconnected = false
        var privacyNavigated = false

        composeTestRule.setContent {
            SettingsScreen(
                state =
                    SettingsUiState(
                        isFlagSecureEnabled = true,
                        isZerodhaConnected = true,
                        isKotakConnected = true,
                    ),
                actions =
                    SettingsActions(
                        onLockNow = { locked = true },
                        onDisconnectBroker = { disconnected = true },
                        onEraseAllData = { erased = true },
                        onNavigateToPrivacy = { privacyNavigated = true },
                    ),
            )
        }

        composeTestRule.onNodeWithText("Lock App Now").performClick()
        assertTrue(locked)

        composeTestRule
            .onNodeWithContentDescription("Open privacy and architecture details")
            .performScrollTo()
            .performClick()
        assertTrue(privacyNavigated)

        composeTestRule
            .onNodeWithContentDescription("Disconnect Zerodha Kite")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Confirm Disconnect").performClick()
        assertTrue(disconnected)

        composeTestRule
            .onNodeWithText("Erase Everything & Reset")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Erase & Reset").performClick()
        assertTrue(erased)
    }

    @Test
    fun testPrivacyScreenContent() {
        var navigatedBack = false

        composeTestRule.setContent {
            PrivacyScreen(onNavigateBack = { navigatedBack = true })
        }

        composeTestRule.onNodeWithText("Privacy & Architecture").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "100% On-Device Execution",
            ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()
        assertTrue(navigatedBack)
    }
}
