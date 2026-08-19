package com.wordscapes.puzzle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Game runs dark-first — a sky gradient is always the actual background;
// M3 surface/background tokens matter only in dialogs and system chrome.
private val DarkColorScheme = darkColorScheme(
    primary          = Blue80,
    secondary        = Teal80,
    tertiary         = BlueGrey80,
    background       = SkyTop,
    surface          = SkyBottom,
    onPrimary        = SkyTop,
    onSecondary      = SkyTop,
    onBackground     = androidx.compose.ui.graphics.Color.White,
    onSurface        = androidx.compose.ui.graphics.Color.White,
)

// Kept for system compliance; not actively used unless the user flips the theme.
private val LightColorScheme = lightColorScheme(
    primary   = Blue40,
    secondary = Teal40,
    tertiary  = BlueGrey40,
)

// No Material You: dynamic colour is Android-only and would override the
// bespoke sky palette anyway.
@Composable
fun WordscapesTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}
