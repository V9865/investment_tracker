package com.personal.investmenttracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.ui.dashboard.DashboardScreen
import com.personal.investmenttracker.ui.dashboard.DashboardUiState
import com.personal.investmenttracker.ui.history.PnlHistoryScreen
import com.personal.investmenttracker.ui.history.PnlHistoryUiState
import com.personal.investmenttracker.ui.holdings.HoldingsScreen
import com.personal.investmenttracker.ui.holdings.HoldingsSortOption
import com.personal.investmenttracker.ui.holdings.HoldingsUiState
import com.personal.investmenttracker.ui.holdings.HoldingsViewMode
import com.personal.investmenttracker.ui.settings.PrivacyScreen
import com.personal.investmenttracker.ui.settings.SettingsActions
import com.personal.investmenttracker.ui.settings.SettingsScreen
import com.personal.investmenttracker.ui.settings.SettingsUiState

@Composable
fun MainScreen(
    state: MainScreenState,
    actions: MainScreenActions,
    modifier: Modifier = Modifier,
) {
    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var showPrivacyPage by remember { mutableStateOf(false) }

    if (showPrivacyPage) {
        PrivacyScreen(
            onNavigateBack = { showPrivacyPage = false },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        bottomBar = {
            MainBottomBar(
                currentTab = currentTab,
                onSelectTab = { currentTab = it },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        MainContent(
            currentTab = currentTab,
            state = state,
            actions = actions,
            innerPadding = innerPadding,
            onShowPrivacy = { showPrivacyPage = true },
        )
    }
}

data class MainScreenState(
    val dashboard: DashboardUiState,
    val holdings: HoldingsUiState,
    val history: PnlHistoryUiState,
    val settings: SettingsUiState,
)

data class MainScreenActions(
    val onSyncClick: () -> Unit,
    val onLockClick: () -> Unit,
    val onReconnectBroker: (Broker) -> Unit,
    val onViewModeSelected: (HoldingsViewMode) -> Unit,
    val onSortSelected: (HoldingsSortOption) -> Unit,
    val onLockTimeoutChange: (Int) -> Unit,
    val onFlagSecureToggle: (Boolean) -> Unit,
    val onDisconnectBroker: (Broker) -> Unit,
    val onEraseAllData: () -> Unit,
)

enum class NavigationTab(
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    HOLDINGS("Holdings", Icons.AutoMirrored.Filled.FormatListBulleted),
    HISTORY("P&L History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
private fun MainBottomBar(
    currentTab: NavigationTab,
    onSelectTab: (NavigationTab) -> Unit,
) {
    NavigationBar {
        NavigationTab.values().forEach { tab ->
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { onSelectTab(tab) },
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                modifier =
                    Modifier.semantics {
                        contentDescription = "${tab.label} tab"
                    },
            )
        }
    }
}

@Composable
private fun MainContent(
    currentTab: NavigationTab,
    state: MainScreenState,
    actions: MainScreenActions,
    innerPadding: PaddingValues,
    onShowPrivacy: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
    ) {
        when (currentTab) {
            NavigationTab.DASHBOARD ->
                DashboardScreen(
                    state = state.dashboard,
                    onSyncClick = actions.onSyncClick,
                    onLockClick = actions.onLockClick,
                    onReconnectBroker = actions.onReconnectBroker,
                )
            NavigationTab.HOLDINGS ->
                HoldingsScreen(
                    state = state.holdings,
                    onViewModeSelected = actions.onViewModeSelected,
                    onSortSelected = actions.onSortSelected,
                )
            NavigationTab.HISTORY ->
                PnlHistoryScreen(
                    state = state.history,
                )
            NavigationTab.SETTINGS ->
                SettingsScreen(
                    state = state.settings,
                    actions =
                        SettingsActions(
                            onLockTimeoutChange = actions.onLockTimeoutChange,
                            onFlagSecureToggle = actions.onFlagSecureToggle,
                            onLockNow = actions.onLockClick,
                            onDisconnectBroker = actions.onDisconnectBroker,
                            onEraseAllData = actions.onEraseAllData,
                            onNavigateToPrivacy = onShowPrivacy,
                        ),
                )
        }
    }
}
