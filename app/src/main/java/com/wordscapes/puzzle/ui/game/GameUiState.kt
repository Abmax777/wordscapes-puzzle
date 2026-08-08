package com.wordscapes.puzzle.ui.game

import androidx.compose.runtime.Immutable
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.WordResult

/**
 * Everything the gameplay screen renders, in one immutable snapshot.
 *
 * One state object exposed as a single StateFlow, rather than several flows
 * the screen has to combine. Multiple flows can be observed at different
 * moments and produce a frame where, say, the grid has updated but the
 * feedback text has not — a class of bug that simply cannot occur when the
 * screen only ever sees one value.
 *
 * Note this holds no gesture state. In-progress selection and pointer position
 * live in WheelGestureState inside the composable, because they are worthless
 * across process death and would otherwise have to be serialised.
 */
@Immutable
data class GameUiState(
    val level: Level? = null,
    val revealedWordIndices: Set<Int> = emptySet(),
    val foundBonusWords: Set<String> = emptySet(),

    /** Outcome of the most recent submission. Null before the first swipe. */
    val lastResult: WordResult? = null,

    /**
     * Monotonic counter, bumped on every submission.
     *
     * This is what lets feedback animations be keyed per submission rather
     * than per screen. Two rapid swipes producing the same [lastResult] would
     * otherwise be indistinguishable to Compose, so the second would not
     * restart the animation — the player swipes twice and sees one shake.
     * Keying on this id makes every submission a distinct animation.
     */
    val submissionId: Long = 0,

    val nextLevelId: Int? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /** True once every word in the grid has been revealed. */
    val isComplete: Boolean
        get() = level != null && revealedWordIndices.size == level.words.size

    val wordsFound: Int get() = revealedWordIndices.size
    val wordsTotal: Int get() = level?.words?.size ?: 0
    val hasNextLevel: Boolean get() = nextLevelId != null
}
