package com.wordscapes.puzzle.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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

/**
 * @param darkTheme     Defaults to true — the game's visual identity is dark.
 * @param dynamicColor  Disabled intentionally: dynamic colour would override
 *                      the bespoke sky palette and GameColors tokens.
 */
@Composable
fun WordscapesTheme(
    darkTheme:    Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}
