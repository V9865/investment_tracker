package com.personal.investmenttracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.personal.investmenttracker.domain.model.DailyPnl
import com.personal.investmenttracker.domain.model.PnlCompleteness
import com.personal.investmenttracker.ui.components.EmptyState
import com.personal.investmenttracker.ui.components.LoadingState
import com.personal.investmenttracker.ui.components.PnlTextBadge
import com.personal.investmenttracker.ui.util.InrFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PnlHistoryScreen(
    state: PnlHistoryUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Daily P&L History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
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
            when {
                state.isLoading -> LoadingState(message = "Loading P&L history...")
                state.historyItems.isEmpty() ->
                    EmptyState(
                        title = "No snapshots recorded yet",
                        message =
                            "End-of-day snapshots are captured automatically " +
                                "after market close (15:45 IST) when the app is open " +
                                "or via background WorkManager.",
                    )
                else -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        items(state.historyItems) { item ->
                            DailyPnlCard(item = item, modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

data class PnlHistoryUiState(
    val isLoading: Boolean = false,
    val historyItems: List<DailyPnl> = emptyList(),
)

@Composable
private fun DailyPnlCard(
    item: DailyPnl,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    val pnlStr = item.pnlAmount.amount.toPlainString()
                    contentDescription = "P&L for ${item.date}: $pnlStr INR, status: ${item.completeness.name}"
                },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                CompletenessBadge(item.completeness)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Total Value",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = InrFormatter.formatInr(item.totalValue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Day P&L",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PnlTextBadge(amount = item.pnlAmount)
                }
            }

            if (item.netTradeCashFlow.amount.signum() != 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Net Trades (Buys - Sells): " + InrFormatter.formatInr(item.netTradeCashFlow),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CompletenessBadge(completeness: PnlCompleteness) {
    val (label, bgColor, textColor) =
        when (completeness) {
            PnlCompleteness.COMPLETE ->
                Triple(
                    "COMPLETE",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
            PnlCompleteness.ESTIMATED ->
                Triple(
                    "ESTIMATED",
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            PnlCompleteness.INCOMPLETE ->
                Triple(
                    "INCOMPLETE",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
        }

    Box(
        modifier =
            Modifier
                .background(bgColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .semantics { contentDescription = "Data status: $label" },
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
