package com.wordscapes.puzzle.domain.model

/**
 * Outcome of a submitted word. [AlreadyFound] is separate from [Invalid]
 * because re-submitting a found word is a different intent from nonsense.
 */
sealed interface WordResult {

    val word: String

    /** An unrevealed word in the grid. Reveals it. */
    data class GridWord(
        override val word: String,
        val wordIndex: Int,
    ) : WordResult

    /** A valid word from the dictionary, but not placed in the grid. */
    data class BonusWord(override val word: String) : WordResult

    /** Already revealed, or already collected as a bonus. */
    data class AlreadyFound(
        override val word: String,
        val wasBonus: Boolean,
    ) : WordResult

    /** Not a word, or too short. Drives the shake. */
    data class Invalid(override val word: String) : WordResult
}
