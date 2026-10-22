package com.personal.investmenttracker.ui.components

import androidx.compose.foundation.clickable
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
import com.personal.investmenttracker.domain.calculator.BrokerSubtotal
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.ui.util.InrFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BrokerSubtotalCard(
    data: BrokerSubtotalData,
    modifier: Modifier = Modifier,
    onReconnect: (() -> Unit)? = null,
) {
    val clickModifier =
        if (data.status != "CONNECTED" && onReconnect != null) {
            Modifier.clickable { onReconnect() }
        } else {
            Modifier
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickModifier)
                .semantics {
                    val totalVal =
                        data.subtotal?.totalCurrentValue?.let {
                            InrFormatter.formatInr(it)
                        } ?: "₹0.00"
                    contentDescription =
                        "${data.broker.displayName} portfolio subtotal: $totalVal, " +
                        "Status: ${data.status}"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            BrokerSubtotalHeader(broker = data.broker, status = data.status)

            Spacer(modifier = Modifier.height(12.dp))

            BrokerSubtotalValues(subtotal = data.subtotal)

            Spacer(modifier = Modifier.height(8.dp))

            BrokerSubtotalInvestedRow(subtotal = data.subtotal)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Last synced: ${formatSyncTime(data.lastSyncTimestamp)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class BrokerSubtotalData(
    val broker: Broker,
    val subtotal: BrokerSubtotal?,
    val lastSyncTimestamp: Long?,
    val status: String,
)

@Composable
private fun BrokerSubtotalHeader(
    broker: Broker,
    status: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = broker.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val statusColor =
            if (status == "CONNECTED") {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        Text(
            text = status.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
        )
    }
}

@Composable
private fun BrokerSubtotalValues(subtotal: BrokerSubtotal?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Current Value",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val totalStr =
                subtotal?.totalCurrentValue?.let {
                    InrFormatter.formatInr(it)
                } ?: "₹0.00"
            Text(
                text = totalStr,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Today's P&L",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PnlTextBadge(
                amount = subtotal?.dailyPnl,
                percent = null,
            )
        }
    }
}

@Composable
private fun BrokerSubtotalInvestedRow(subtotal: BrokerSubtotal?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val investedStr =
            subtotal?.totalCostBasis?.let {
                InrFormatter.formatInr(it)
            } ?: "₹0.00"
        Text(
            text = "Invested: $investedStr",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Unrealized: ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PnlTextBadge(
                amount = subtotal?.unrealizedPnl,
                percent = subtotal?.unrealizedPnlPercent,
            )
        }
    }
}

private fun formatSyncTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "Never"
    return try {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = instant.atZone(ZoneId.of("Asia/Kolkata"))
        DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").format(zonedDateTime)
    } catch (_: Exception) {
        "Never"
    }
}
