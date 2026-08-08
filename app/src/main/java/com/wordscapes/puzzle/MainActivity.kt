package com.wordscapes.puzzle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.wordscapes.puzzle.ui.navigation.WordscapesNavGraph
import com.wordscapes.puzzle.ui.theme.WordscapesTheme

/**
 * The single Activity in the app.
 *
 * enableEdgeToEdge() lets Compose draw under the status bar and nav bar, which
 * matters for the full-screen sky gradient we're using as the game background.
 * The Compose side handles window inset consumption (WindowInsets.safeDrawing).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WordscapesTheme {
                WordscapesNavGraph()
            }
        }
    }
}
