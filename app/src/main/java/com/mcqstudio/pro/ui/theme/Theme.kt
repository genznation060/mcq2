package com.mcqstudio.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StudioDarkBg = Color(0xFF0C0E17)
val StudioCardBg = Color(0xFF141824)
val StudioSurfaceVariant = Color(0xFF1F2436)
val StudioAccent = Color(0xFF8A2BE2) // Violet Neon
val StudioSecondary = Color(0xFFFFB300) // Amber Gold
val StudioEmerald = Color(0xFF00E676)
val StudioError = Color(0xFFFF5252)

private val DarkColorScheme = darkColorScheme(
    primary = StudioAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF321359),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = StudioSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A3400),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = StudioEmerald,
    background = StudioDarkBg,
    onBackground = Color(0xFFF1F3F9),
    surface = StudioCardBg,
    onSurface = Color(0xFFF1F3F9),
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = Color(0xFFC7CBD8),
    error = StudioError,
    onError = Color.White
)

@Composable
fun MCQStudioProTheme(
    darkTheme: Boolean = true, // Always prefer professional dark video editor theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
