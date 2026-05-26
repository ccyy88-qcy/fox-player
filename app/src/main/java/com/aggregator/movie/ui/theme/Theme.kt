package com.aggregator.movie.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 影视聚合主题色
val OrangePrimary = Color(0xFFFF6D00)
val OrangeSecondary = Color(0xFFFF9800)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2C2C2C)
val TextPrimary = Color(0xFFE0E0E0)
val TextSecondary = Color(0xFF999999)
val ErrorColor = Color(0xFFCF6679)
val DividerColor = Color(0xFF333333)

private val DarkColorScheme = darkColorScheme(
    primary = OrangePrimary,
    onPrimary = Color.White,
    secondary = OrangeSecondary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorColor,
    outline = DividerColor
)

@Composable
fun MovieAggregatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = androidx.compose.material3.Typography,
        content = content
    )
}
