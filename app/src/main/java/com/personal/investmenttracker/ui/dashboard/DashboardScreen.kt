package com.personal.investmenttracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.investmenttracker.domain.calculator.BrokerSubtotal
import com.personal.investmenttracker.domain.calculator.PortfolioSummary
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.ui.components.BrokerSubtotalCard
import com.personal.investmenttracker.ui.components.EmptyState
import com.personal.investmenttracker.ui.components.ErrorRetryState
import com.personal.investmenttracker.ui.components.LoadingState
import com.personal.investmenttracker.ui.components.OfflineBanner
import com.personal.investmenttracker.ui.components.PnlTextBadge
import com.personal.investmenttracker.ui.components.StaleBanner
import com.personal.investmenttracker.ui.util.InrFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onSyncClick: () -> Unit,
    onLockClick: () -> Unit,
    onReconnectBroker: (Broker) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            DashboardTopBar(onSyncClick = onSyncClick, onLockClick = onLockClick)
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            DashboardBanners(state = state, onReconnectBroker = onReconnectBroker)

            DashboardBody(
                state = state,
                onSyncClick = onSyncClick,
                onReconnectBroker = onReconnectBroker,
            )
        }
    }
}

data class BrokerCardUiModel(
    val broker: Broker,
    val subtotal: BrokerSubtotal?,
    val lastSyncAt: Long?,
    val status: String,
    val sessionExpired: Boolean = false,
    val bannerMessage: String = "",
)

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val portfolioSummary: PortfolioSummary? = null,
    val brokerCards: List<BrokerCardUiModel> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    onSyncClick: () -> Unit,
    onLockClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            IconButton(
                onClick = onSyncClick,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Sync Portfolios"
                    },
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            }
            IconButton(
                onClick = onLockClick,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Lock App"
                    },
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null)
            }
        },
    )
}

@Composable
private fun DashboardBanners(
    state: DashboardUiState,
    onReconnectBroker: (Broker) -> Unit,
) {
    if (state.isOffline) {
        OfflineBanner(modifier = Modifier.padding(bottom = 12.dp))
    }

    for (card in state.brokerCards) {
        if (card.sessionExpired && card.bannerMessage.isNotBlank()) {
            StaleBanner(
                message = card.bannerMessage,
                onRefresh = { onReconnectBroker(card.broker) },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun DashboardBody(
    state: DashboardUiState,
    onSyncClick: () -> Unit,
    onReconnectBroker: (Broker) -> Unit,
) {
    when {
        state.isLoading -> {
            LoadingState(message = "Syncing Zerodha and Kotak portfolios...")
        }
        state.errorMessage != null -> {
            ErrorRetryState(
                errorMessage = state.errorMessage,
                onRetry = onSyncClick,
            )
        }
        state.portfolioSummary == null ||
            (
                state.portfolioSummary.totalCurrentValue.amount
                    .signum() == 0 &&
                    state.brokerCards.all { it.subtotal?.activeHoldingsCount == 0 }
            ) -> {
            EmptyState(
                title = "No holdings found",
                message =
                    "Connect Zerodha Kite or Kotak Neo in settings to view " +
                        "your portfolio and today's P&L.",
            )
        }
        else -> {
            DashboardContent(
                summary = state.portfolioSummary,
                brokerCards = state.brokerCards,
                onReconnectBroker = onReconnectBroker,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    summary: PortfolioSummary,
    brokerCards: List<BrokerCardUiModel>,
    onReconnectBroker: (Broker) -> Unit,
) {
    SummaryCard(summary = summary)

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Broker Subtotals",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    for (card in brokerCards) {
        com.personal.investmenttracker.ui.components.BrokerSubtotalCard(
            data =
                com.personal.investmenttracker.ui.components.BrokerSubtotalData(
                    broker = card.broker,
                    subtotal = card.subtotal,
                    lastSyncTimestamp = card.lastSyncAt,
                    status = card.status,
                ),
            onReconnect = { onReconnectBroker(card.broker) },
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun SummaryCard(summary: PortfolioSummary) {
    val totalValStr = InrFormatter.formatInr(summary.totalCurrentValue)
    val investedStr = InrFormatter.formatInr(summary.totalCostBasis)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "Total portfolio value: $totalValStr, Invested: $investedStr"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Total Portfolio Value",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = totalValStr,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(16.dp))

            SummaryCardMetrics(summary = summary)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Total Invested: $investedStr",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun SummaryCardMetrics(summary: PortfolioSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Today's P&L",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            PnlTextBadge(
                amount = summary.dailyPnl,
                percent = null,
                isLarge = true,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Unrealized P&L",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            PnlTextBadge(
                amount = summary.unrealizedPnl,
                percent = summary.unrealizedPnlPercent,
                isLarge = true,
            )
        }
    }
}
