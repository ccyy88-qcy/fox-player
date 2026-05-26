package com.aggregator.movie.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// === 红色品牌主色（新浅色主题） ===
val RedPrimary = Color(0xFFE53935)
val RedLight = Color(0xFFFFEBEE)
val RedDark = Color(0xFFC62828)
val LightBg = Color(0xFFF5F5F5)
val LightSurface = Color.White
val LightCardBg = Color.White
val TextDark = Color(0xFF212121)
val TextGray = Color(0xFF757575)
val TextLightGray = Color(0xFFBDBDBD)
val DividerLight = Color(0xFFEEEEEE)

// === 旧橙色深色主题（向后兼容） ===
val OrangePrimary = Color(0xFFFF6D00)
val OrangeSecondary = Color(0xFFFF9800)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2C2C2C)
val TextPrimary = Color(0xFFE0E0E0)
val TextSecondary = Color(0xFF999999)
val ErrorColor = Color(0xFFCF6679)
val DividerColor = Color(0xFF333333)

private val LightColorScheme = lightColorScheme(
    primary = RedPrimary,
    onPrimary = Color.White,
    secondary = RedPrimary,
    background = LightBg,
    onBackground = TextDark,
    surface = LightSurface,
    onSurface = TextDark,
    surfaceVariant = LightCardBg,
    onSurfaceVariant = TextGray,
    error = ErrorColor,
    outline = DividerLight
)

@Composable
fun MovieAggregatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
