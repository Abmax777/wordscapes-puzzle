package com.wordscapes.puzzle.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe destinations. @Serializable so route args encode without URI strings. */
sealed interface Destination {

    @Serializable
    data object Home : Destination

    @Serializable
    data object LevelSelect : Destination

    /** ViewModel from koinViewModel() is scoped to this entry, with its own SavedStateHandle. */
    @Serializable
    data class Game(val levelId: Int) : Destination

    /**
     * Registered with `dialog<T>`, so it sits on top of Game rather than replacing
     * it: Game stays composed and Back dismisses only the dialog.
     */
    @Serializable
    data class Pause(val levelId: Int) : Destination
}
