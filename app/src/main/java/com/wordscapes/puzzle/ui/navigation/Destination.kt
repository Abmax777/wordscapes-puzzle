package com.wordscapes.puzzle.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations (Navigation Compose 2.8+).
 *
 * Each entry is @Serializable so the library can encode route arguments into
 * the back stack without hand-built URI strings.
 */
sealed interface Destination {

    /** Entry point — title screen. */
    @Serializable
    data object Home : Destination

    /** Grid of level cards. */
    @Serializable
    data object LevelSelect : Destination

    /**
     * Active gameplay.
     *
     * [levelId] is the key in levels.json and in DataStore progress. The
     * ViewModel obtained via hiltViewModel() is scoped to this back-stack
     * entry, so each Game destination gets its own instance and its own
     * SavedStateHandle.
     */
    @Serializable
    data class Game(val levelId: Int) : Destination

    /**
     * Pause menu.
     *
     * Registered with `dialog<T>` rather than `composable<T>`, which is the
     * whole point. A dialog destination sits ON TOP of the Game destination
     * rather than replacing it: Game stays composed, its ViewModel and
     * SavedStateHandle stay alive, and Back dismisses only the dialog. Built
     * as a screen instead, Back would pop the player out of the level and
     * pausing would cost them their board.
     *
     * [levelId] is carried so Restart can rebuild the same level without
     * reaching across into the Game entry's ViewModel.
     */
    @Serializable
    data class Pause(val levelId: Int) : Destination
}
