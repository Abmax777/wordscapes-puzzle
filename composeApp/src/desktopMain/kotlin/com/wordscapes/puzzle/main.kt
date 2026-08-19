package com.wordscapes.puzzle

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wordscapes.puzzle.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Wordscapes",
            // Phone-ish aspect: the layout is portrait-first.
            state = rememberWindowState(size = DpSize(420.dp, 860.dp)),
        ) {
            App()
        }
    }
}
