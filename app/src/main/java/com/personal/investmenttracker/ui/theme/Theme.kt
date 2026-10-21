package com.personal.investmenttracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = PrimaryBlue,
        secondary = AccentGreen,
        background = DarkBackground,
        surface = SurfaceDark,
        onPrimary = DarkBackground,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
    )

@Composable
fun InvestmentTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
