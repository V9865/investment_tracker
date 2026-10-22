package com.personal.investmenttracker.ui.holdings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.investmenttracker.domain.calculator.ConsolidatedHolding
import com.personal.investmenttracker.domain.calculator.HoldingCalculation
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.ui.components.EmptyState
import com.personal.investmenttracker.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingsScreen(
    state: HoldingsUiState,
    onViewModeSelected: (HoldingsViewMode) -> Unit,
    onSortSelected: (HoldingsSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HoldingsTopBar(
                selectedSort = state.selectedSort,
                showMenu = showSortMenu,
                onToggleMenu = { showSortMenu = it },
                onSelectSort = onSortSelected,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            HoldingsTabRow(
                selectedMode = state.selectedViewMode,
                onSelectMode = onViewModeSelected,
            )

            HoldingsBody(state = state)
        }
    }
}

enum class HoldingsViewMode {
    CONSOLIDATED,
    ZERODHA,
    KOTAK_NEO,
}

enum class HoldingsSortOption(
    val displayName: String,
) {
    VALUE_DESC("Highest Value"),
    DAY_PNL_DESC("Today's Gainers"),
    UNREALIZED_DESC("Highest Unrealized Gain"),
    SYMBOL_ASC("Symbol (A-Z)"),
}

data class HoldingsUiState(
    val isLoading: Boolean = false,
    val selectedViewMode: HoldingsViewMode = HoldingsViewMode.CONSOLIDATED,
    val selectedSort: HoldingsSortOption = HoldingsSortOption.VALUE_DESC,
    val consolidatedHoldings: List<ConsolidatedHolding> = emptyList(),
    val brokerHoldings: List<HoldingCalculation> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoldingsTopBar(
    selectedSort: HoldingsSortOption,
    showMenu: Boolean,
    onToggleMenu: (Boolean) -> Unit,
    onSelectSort: (HoldingsSortOption) -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "Holdings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            Box {
                IconButton(
                    onClick = { onToggleMenu(true) },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Sort holdings by ${selectedSort.displayName}"
                        },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onToggleMenu(false) },
                ) {
                    for (option in HoldingsSortOption.values()) {
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                onSelectSort(option)
                                onToggleMenu(false)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun HoldingsTabRow(
    selectedMode: HoldingsViewMode,
    onSelectMode: (HoldingsViewMode) -> Unit,
) {
    val tabs =
        listOf(
            HoldingsViewMode.CONSOLIDATED to "Consolidated",
            HoldingsViewMode.ZERODHA to "Zerodha",
            HoldingsViewMode.KOTAK_NEO to "Kotak Neo",
        )
    val selectedIndex = tabs.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0)

    TabRow(selectedTabIndex = selectedIndex) {
        tabs.forEachIndexed { index, (mode, label) ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelectMode(mode) },
                text = { Text(label, fontWeight = FontWeight.SemiBold) },
            )
        }
    }
}

@Composable
private fun HoldingsBody(state: HoldingsUiState) {
    if (state.isLoading) {
        LoadingState(message = "Loading holdings...")
        return
    }

    if (state.selectedViewMode == HoldingsViewMode.CONSOLIDATED) {
        if (state.consolidatedHoldings.isEmpty()) {
            EmptyState(title = "No holdings", message = "No consolidated holdings to display.")
        } else {
            ConsolidatedHoldingsList(state.consolidatedHoldings)
        }
    } else {
        val targetBroker =
            if (state.selectedViewMode == HoldingsViewMode.ZERODHA) {
                Broker.ZERODHA
            } else {
                Broker.KOTAK_NEO
            }
        val filtered = state.brokerHoldings.filter { it.holding.broker == targetBroker }
        if (filtered.isEmpty()) {
            EmptyState(title = "No holdings", message = "No holdings synced for this broker.")
        } else {
            BrokerHoldingsList(filtered)
        }
    }
}

@Composable
private fun ConsolidatedHoldingsList(holdings: List<ConsolidatedHolding>) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(holdings) { item ->
            ConsolidatedHoldingCard(item = item, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun BrokerHoldingsList(holdings: List<HoldingCalculation>) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(holdings) { item ->
            BrokerHoldingCard(item = item, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}
