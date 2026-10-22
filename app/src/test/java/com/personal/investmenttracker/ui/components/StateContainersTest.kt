package com.personal.investmenttracker.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StateContainersTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLoadingState() {
        composeTestRule.setContent {
            LoadingState(message = "Fetching holdings...")
        }
        composeTestRule.onNodeWithText("Fetching holdings...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun testEmptyState() {
        composeTestRule.setContent {
            EmptyState(
                title = "No holdings found",
                message = "Add your investments to see them here.",
            )
        }
        composeTestRule.onNodeWithText("No holdings found").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Add your investments to see them here.",
            ).assertIsDisplayed()
    }

    @Test
    fun testOfflineState() {
        var retried = false
        composeTestRule.setContent {
            OfflineState(onRetry = { retried = true })
        }
        composeTestRule.onNodeWithText("No internet connection").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun testStaleState() {
        var refreshed = false
        composeTestRule.setContent {
            StaleState(onRefresh = { refreshed = true })
        }
        composeTestRule.onNodeWithText("Data may be stale").assertIsDisplayed()
        composeTestRule.onNodeWithText("Refresh Now").performClick()
        assertTrue(refreshed)
    }

    @Test
    fun testErrorRetryState() {
        var retried = false
        composeTestRule.setContent {
            ErrorRetryState(
                errorMessage = "Network timeout occurred",
                onRetry = { retried = true },
            )
        }
        composeTestRule.onNodeWithText("Network timeout occurred").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }
}
