package com.personal.investmenttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = BrandPrimaryDark,
        secondary = BrandSecondaryDark,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = DarkSurfaceElevated,
        outline = DarkBorder,
        onPrimary = DarkBackground,
        onSecondary = DarkBackground,
        onBackground = DarkTextPrimary,
        onSurface = DarkTextPrimary,
        onSurfaceVariant = DarkTextSecondary,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = BrandPrimary,
        secondary = BrandSecondary,
        background = LightBackground,
        surface = LightSurface,
        surfaceVariant = LightSurfaceElevated,
        outline = LightBorder,
        onPrimary = LightSurface,
        onSecondary = LightSurface,
        onBackground = LightTextPrimary,
        onSurface = LightTextPrimary,
        onSurfaceVariant = LightTextSecondary,
    )

@Composable
fun InvestmentTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
