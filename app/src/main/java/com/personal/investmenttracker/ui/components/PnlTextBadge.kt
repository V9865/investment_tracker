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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.ui.theme.GainGreenBgDark
import com.personal.investmenttracker.ui.theme.GainGreenBgLight
import com.personal.investmenttracker.ui.theme.GainGreenDark
import com.personal.investmenttracker.ui.theme.GainGreenLight
import com.personal.investmenttracker.ui.theme.LossRedBgDark
import com.personal.investmenttracker.ui.theme.LossRedBgLight
import com.personal.investmenttracker.ui.theme.LossRedDark
import com.personal.investmenttracker.ui.theme.LossRedLight
import com.personal.investmenttracker.ui.util.InrFormatter
import java.math.BigDecimal

@Composable
fun PnlTextBadge(
    amount: Money?,
    percent: BigDecimal? = null,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
) {
    val pnl = InrFormatter.formatPnl(amount, percent)
    val isDark = isSystemInDarkTheme()

    val (textColor, bgColor) =
        when {
            pnl.isPositive -> if (isDark) GainGreenDark to GainGreenBgDark else GainGreenLight to GainGreenBgLight
            pnl.isNegative -> if (isDark) LossRedDark to LossRedBgDark else LossRedLight to LossRedBgLight
            else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
        }

    val fontSize = if (isLarge) 18.sp else 13.sp
    val paddingHorizontal = if (isLarge) 12.dp else 8.dp
    val paddingVertical = if (isLarge) 6.dp else 4.dp

    Box(
        modifier =
            modifier
                .background(bgColor, RoundedCornerShape(6.dp))
                .padding(horizontal = paddingHorizontal, vertical = paddingVertical)
                .semantics {
                    contentDescription = pnl.accessibilityDescription
                },
    ) {
        Text(
            text = pnl.displayText,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
