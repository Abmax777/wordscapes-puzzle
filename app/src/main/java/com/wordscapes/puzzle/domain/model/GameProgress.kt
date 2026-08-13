package com.wordscapes.puzzle.domain.model

import androidx.compose.runtime.Immutable

/**
 * Completed levels only. Unlock state is derived, never stored — two persisted
 * fields that must agree are two fields that can disagree.
 */
@Immutable
data class GameProgress(
    val completedLevelIds: Set<Int> = emptySet(),
) {
    fun isCompleted(levelId: Int): Boolean = levelId in completedLevelIds

    /** First level is always open; others open when the previous is complete.
     *  An unknown id is locked rather than throwing. */
    fun isUnlocked(levelId: Int, orderedLevelIds: List<Int>): Boolean {
        val index = orderedLevelIds.indexOf(levelId)
        return when {
            index < 0 -> false
            index == 0 -> true
            else -> orderedLevelIds[index - 1] in completedLevelIds
        }
    }

    /** First unfinished level, or the last one once everything is done. */
    fun nextPlayableLevelId(orderedLevelIds: List<Int>): Int? =
        orderedLevelIds.firstOrNull { it !in completedLevelIds }
            ?: orderedLevelIds.lastOrNull()

    val completedCount: Int get() = completedLevelIds.size
}
