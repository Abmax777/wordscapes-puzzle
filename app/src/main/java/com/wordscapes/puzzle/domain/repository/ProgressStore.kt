package com.wordscapes.puzzle.domain.repository

import com.wordscapes.puzzle.domain.model.GameProgress
import kotlinx.coroutines.flow.Flow

/**
 * Cross-session progress. A Flow rather than a getter, so completing a level
 * updates Level Select and Home without any screen knowing the others exist.
 */
interface ProgressStore {
    val progress: Flow<GameProgress>

    suspend fun markCompleted(levelId: Int)

    /** Not reachable from the UI. Useful in testing. */
    suspend fun reset()
}
