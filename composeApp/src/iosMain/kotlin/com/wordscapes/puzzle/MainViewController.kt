package com.wordscapes.puzzle

import androidx.compose.ui.window.ComposeUIViewController
import com.wordscapes.puzzle.di.initKoin
import platform.UIKit.UIViewController

// iOS has no Application class, so Koin starts on first controller creation.
// Guarded because UIKit may build the controller more than once.
private var koinStarted = false

fun MainViewController(): UIViewController {
    if (!koinStarted) {
        initKoin()
        koinStarted = true
    }
    return ComposeUIViewController { App() }
}
