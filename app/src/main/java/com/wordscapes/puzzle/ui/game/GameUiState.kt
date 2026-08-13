package com.wordscapes.puzzle.ui.game

import androidx.compose.runtime.Immutable
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.WordResult

/**
 * One immutable snapshot per frame, so the screen can never see a half-updated
 * state. Holds no gesture state — that lives in WheelGestureState.
 */
@Immutable
data class GameUiState(
    val level: Level? = null,
    val revealedWordIndices: Set<Int> = emptySet(),
    val foundBonusWords: Set<String> = emptySet(),

    /** Null before the first swipe. */
    val lastResult: WordResult? = null,

    /** Bumped per submission so animations key on it. Two identical rapid results
     *  would otherwise look unchanged to Compose and produce one animation. */
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
