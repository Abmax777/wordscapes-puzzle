package com.wordscapes.puzzle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.wordscapes.puzzle.ui.navigation.WordscapesNavGraph
import com.wordscapes.puzzle.ui.theme.WordscapesTheme

/** The single Activity. Compose handles insets via WindowInsets.safeDrawing. */
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
