package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AuraCyanPrimary,
    onPrimary = Color(0xFF041E28),
    primaryContainer = Color(0xFF0B384D),
    onPrimaryContainer = AuraCyanBright,
    secondary = AuraVioletSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C1952),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = AuraPinkTertiary,
    onTertiary = Color.White,
    background = AuraDarkBackground,
    onBackground = TextPrimary,
    surface = AuraDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = AuraDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = AuraCardBorder,
    error = AuraError,
    onError = Color.White
)

private val LightColorScheme = darkColorScheme( // Assistant has a dedicated sleek dark sci-fi theme
    primary = AuraCyanPrimary,
    onPrimary = Color(0xFF041E28),
    primaryContainer = Color(0xFF0B384D),
    onPrimaryContainer = AuraCyanBright,
    secondary = AuraVioletSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C1952),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = AuraPinkTertiary,
    onTertiary = Color.White,
    background = AuraDarkBackground,
    onBackground = TextPrimary,
    surface = AuraDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = AuraDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = AuraCardBorder,
    error = AuraError,
    onError = Color.White
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AuraTheme(darkTheme, dynamicColor, content)
}
