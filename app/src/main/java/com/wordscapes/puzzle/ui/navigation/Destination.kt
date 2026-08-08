package com.wordscapes.puzzle.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations (Navigation Compose 2.8+).
 *
 * Each object/class is @Serializable so the nav library can encode route
 * arguments into the back stack without manually building URI strings.
 * This replaces the older "route = \"game/{levelId}\"" string pattern.
 *
 * Analogy for the platform side: think of these like Binder transaction codes —
 * the serialization layer is the parcelling; composable<T>() is the stub/proxy.
 */
sealed interface Destination {

    /** Entry point — title screen with PLAY button. */
    @Serializable
    data object Home : Destination

    /** Grid of level cards. Navigated to from Home; navigates into Game. */
    @Serializable
    data object LevelSelect : Destination

    /**
     * Active gameplay screen.
     *
     * [levelId] is the primary key in levels.json and in DataStore progress.
     * The ViewModel loaded via hiltViewModel() is scoped to this back-stack
     * entry, so each Game destination gets its own ViewModel instance.
     */
    @Serializable
    data class Game(val levelId: Int) : Destination

    /**
     * Development-only sandbox for building the letter wheel in isolation.
     *
     * REMOVE before submitting, along with its NavGraph entry and the Home
     * screen button that reaches it. Day 6 cleanup item.
     */
    @Serializable
    data object WheelSandbox : Destination
}
