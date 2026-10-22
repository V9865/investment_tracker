package com.personal.investmenttracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.ui.theme.StaleWarningBgDark
import com.personal.investmenttracker.ui.theme.StaleWarningBgLight
import com.personal.investmenttracker.ui.theme.StaleWarningDark
import com.personal.investmenttracker.ui.theme.StaleWarningLight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FreshnessChip(
    quote: Quote?,
    modifier: Modifier = Modifier,
) {
    if (quote == null) {
        Box(
            modifier =
                modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp),
                    ).padding(horizontal = 6.dp, vertical = 2.dp)
                    .semantics {
                        contentDescription =
                            "Price quote not available from current data source"
                    },
        ) {
            Text(
                text = "Not available from current data source",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        return
    }

    val isDark = isSystemInDarkTheme()
    val chipConfig = resolveChipConfig(quote, isDark)

    Box(
        modifier =
            modifier
                .background(chipConfig.bgColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .semantics {
                    contentDescription = chipConfig.talkBackLabel
                },
    ) {
        Text(
            text = chipConfig.label,
            color = chipConfig.textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class ChipConfig(
    val label: String,
    val textColor: Color,
    val bgColor: Color,
    val talkBackLabel: String,
)

@Composable
private fun resolveChipConfig(
    quote: Quote,
    isDark: Boolean,
): ChipConfig =
    when (quote.freshness) {
        Freshness.LIVE -> {
            val timeStr = formatTimestamp(quote.sourceTimestamp)
            val label = if (timeStr.isBlank()) "LIVE" else "LIVE ($timeStr)"
            val talkBack = if (timeStr.isBlank()) "Live quote" else "Live quote updated at $timeStr"
            ChipConfig(
                label = label,
                textColor = MaterialTheme.colorScheme.primary,
                bgColor = MaterialTheme.colorScheme.surfaceVariant,
                talkBackLabel = talkBack,
            )
        }
        Freshness.DELAYED -> {
            ChipConfig(
                label = "DELAYED",
                textColor = if (isDark) StaleWarningDark else StaleWarningLight,
                bgColor = if (isDark) StaleWarningBgDark else StaleWarningBgLight,
                talkBackLabel = "Quote delayed",
            )
        }
        Freshness.STALE -> {
            ChipConfig(
                label = "STALE",
                textColor = if (isDark) StaleWarningDark else StaleWarningLight,
                bgColor = if (isDark) StaleWarningBgDark else StaleWarningBgLight,
                talkBackLabel = "Quote data is stale",
            )
        }
        Freshness.EOD -> {
            ChipConfig(
                label = "EOD",
                textColor = MaterialTheme.colorScheme.secondary,
                bgColor = MaterialTheme.colorScheme.surfaceVariant,
                talkBackLabel = "End of day official close quote",
            )
        }
    }

private fun formatTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0) return ""
    return try {
        val instant = Instant.ofEpochMilli(timestampMs)
        val zonedDateTime = instant.atZone(ZoneId.of("Asia/Kolkata"))
        DateTimeFormatter.ofPattern("h:mm a").format(zonedDateTime)
    } catch (_: Exception) {
        ""
    }
}
