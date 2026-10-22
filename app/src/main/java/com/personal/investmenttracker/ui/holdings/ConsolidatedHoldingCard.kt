package com.personal.investmenttracker.ui.holdings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.personal.investmenttracker.domain.calculator.ConsolidatedHolding
import com.personal.investmenttracker.ui.components.PnlTextBadge
import com.personal.investmenttracker.ui.util.InrFormatter

@Composable
fun ConsolidatedHoldingCard(
    item: ConsolidatedHolding,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    val valueStr = item.totalCurrentValue?.let { InrFormatter.formatInr(it) } ?: "N/A"
                    val qtyStr = item.totalQuantity.value.toPlainString()
                    contentDescription = "${item.symbol}: $qtyStr shares, value: $valueStr"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            ConsolidatedCardHeader(item = item)

            Spacer(modifier = Modifier.height(10.dp))

            ConsolidatedCardPnlRow(item = item)

            Spacer(modifier = Modifier.height(8.dp))

            ConsolidatedCardBrokersRow(item = item)
        }
    }
}

@Composable
private fun ConsolidatedCardHeader(item: ConsolidatedHolding) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = item.symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${item.listingKey.exchange.name} • ISIN: ${item.listingKey.isin}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            val totalStr = item.totalCurrentValue?.let { InrFormatter.formatInr(it) } ?: "₹0.00"
            Text(
                text = totalStr,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Qty: ${item.totalQuantity.value.toPlainString()}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConsolidatedCardPnlRow(item: ConsolidatedHolding) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Day P&L: ",
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

@Composable
private fun ConsolidatedCardBrokersRow(item: ConsolidatedHolding) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Brokers: ",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (h in item.brokerHoldings) {
            Box(
                modifier =
                    Modifier
                        .padding(end = 4.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${h.broker.displayName} (${h.quantity.value.toPlainString()})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
