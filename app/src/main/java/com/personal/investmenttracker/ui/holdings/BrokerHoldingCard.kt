package com.personal.investmenttracker.ui.holdings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.investmenttracker.domain.calculator.HoldingCalculation
import com.personal.investmenttracker.ui.components.FreshnessChip
import com.personal.investmenttracker.ui.components.PnlTextBadge
import com.personal.investmenttracker.ui.util.InrFormatter

@Composable
fun BrokerHoldingCard(
    item: HoldingCalculation,
    modifier: Modifier = Modifier,
) {
    val h = item.holding
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    val valueStr = item.currentValue?.let { InrFormatter.formatInr(it) } ?: "N/A"
                    val qtyStr = h.quantity.value.toPlainString()
                    contentDescription = "${h.symbol} at ${h.broker.displayName}: " +
                        "$qtyStr shares, value: $valueStr"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            BrokerCardHeader(item = item)

            Spacer(modifier = Modifier.height(10.dp))

            BrokerCardPnlRow(item = item)

            Spacer(modifier = Modifier.height(8.dp))

            FreshnessChip(quote = h.quote)
        }
    }
}

@Composable
private fun BrokerCardHeader(item: HoldingCalculation) {
    val h = item.holding
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = h.symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${h.exchange.name} • ${h.broker.displayName}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            val curValStr = item.currentValue?.let { InrFormatter.formatInr(it) } ?: "₹0.00"
            Text(
                text = curValStr,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val avgStr =
                if (h.isCostUnknown) {
                    " • (Cost unknown)"
                } else {
                    " • Avg: ${h.avgPriceReported?.let { InrFormatter.formatInr(it) }}"
                }
            Text(
                text = "Qty: ${h.quantity.value.toPlainString()}$avgStr",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrokerCardPnlRow(item: HoldingCalculation) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Day: ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PnlTextBadge(amount = item.dailyPnl, percent = item.dailyPnlPercent)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Unrealized: ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PnlTextBadge(amount = item.unrealizedPnl, percent = item.unrealizedPnlPercent)
        }
    }
}
