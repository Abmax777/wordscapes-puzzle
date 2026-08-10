package com.wordscapes.puzzle.domain.model

/**
 * Cross-session progress. Survives the app being killed, updated, or the
 * device rebooted — unlike SavedStateHandle, which only survives within one
 * install session.
 *
 * Stores only which levels are *completed*. Which levels are *unlocked* is
 * derived from that plus the level ordering, rather than stored alongside it.
 * Two fields that must agree are two fields that can disagree: persist a
 * highest-unlocked value and any bug, migration or hand-edit can leave it
 * pointing at a level whose predecessor was never finished.
 */
data class GameProgress(
    val completedLevelIds: Set<Int> = emptySet(),
) {
    fun isCompleted(levelId: Int): Boolean = levelId in completedLevelIds

    /**
     * Whether [levelId] can be played, given the full ordered list of level ids.
     *
     * The first level is always open; every other level opens when the one
     * before it is completed. An unknown id is treated as locked rather than
     * throwing — level content can change between app versions, and a stale id
     * should degrade to "not playable", not crash the level list.
     */
    fun isUnlocked(levelId: Int, orderedLevelIds: List<Int>): Boolean {
        val index = orderedLevelIds.indexOf(levelId)
        return when {
            index < 0 -> false
            index == 0 -> true
            else -> orderedLevelIds[index - 1] in completedLevelIds
        }
    }

    /**
     * Where a Continue button should go: the first level not yet completed,
     * or the last level once everything is done.
     */
    fun nextPlayableLevelId(orderedLevelIds: List<Int>): Int? =
        orderedLevelIds.firstOrNull { it !in completedLevelIds }
            ?: orderedLevelIds.lastOrNull()

    val completedCount: Int get() = completedLevelIds.size
}
