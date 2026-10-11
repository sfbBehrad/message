package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = CyberCyanDark,
    onPrimaryContainer = Color(0xFF7BF2FF),
    secondary = CyberAmber,
    onSecondary = Color(0xFF432C00),
    secondaryContainer = Color(0xFF593B00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = CyberGreen,
    background = DeepNavyDark,
    onBackground = TextBright,
    surface = CardNavy,
    onSurface = TextBright,
    surfaceVariant = CardNavyHover,
    onSurfaceVariant = TextMuted,
    outline = BorderNavy
)

private val LightColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = CyberCyanDark,
    onPrimaryContainer = Color(0xFF7BF2FF),
    secondary = CyberAmber,
    onSecondary = Color(0xFF432C00),
    background = DeepNavyDark,
    onBackground = TextBright,
    surface = CardNavy,
    onSurface = TextBright
)

@Composable
fun RadioBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
