package com.wordscapes.puzzle

import androidx.compose.runtime.Composable
import com.wordscapes.puzzle.ui.navigation.WordscapesNavGraph
import com.wordscapes.puzzle.ui.theme.WordscapesTheme

/** The whole app above the platform entry point. Shared by Android, iOS and desktop. */
@Composable
fun App() {
    WordscapesTheme {
        WordscapesNavGraph()
    }
}
