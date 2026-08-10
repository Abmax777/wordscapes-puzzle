package com.wordscapes.puzzle.domain.repository

import com.wordscapes.puzzle.domain.model.GameProgress
import kotlinx.coroutines.flow.Flow

/**
 * Durable, cross-session progress.
 *
 * Exposed as a [Flow] rather than a suspend getter so screens observe changes
 * instead of polling: completing a level in Game updates Level Select without
 * either screen knowing the other exists.
 *
 * An interface for the same reason as WordLookup and LevelCatalog — the
 * implementation needs an Android Context, and ViewModels that depend on it
 * should still be testable on the JVM.
 */
interface ProgressStore {
    val progress: Flow<GameProgress>

    suspend fun markCompleted(levelId: Int)

    /** Wipes progress. Not reachable from the UI; useful in testing. */
    suspend fun reset()
}
