package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAmber,
    secondary = SecondaryNavy,
    tertiary = AccentOrange,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = BackgroundDark,
    onSecondary = TextLight,
    onBackground = TextLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextLight
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAmber,
    secondary = SecondaryNavy,
    tertiary = AccentOrange,
    background = BackgroundDark, // Retain dark immersive feel for premium theme
    surface = SurfaceDark,
    onPrimary = BackgroundDark,
    onSecondary = TextLight,
    onBackground = TextLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Explicitly pin our beautiful theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
