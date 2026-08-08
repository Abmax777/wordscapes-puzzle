package com.wordscapes.puzzle.domain.model

/**
 * The outcome of submitting a swiped word.
 *
 * The brief grades "three distinct validation feedbacks" — grid word, bonus
 * word, invalid. [AlreadyFound] is a fourth, separated out because
 * re-submitting a word you have already found is a distinct user intent from
 * submitting nonsense, and giving both the same red shake reads as a bug.
 *
 * Resolution order in ValidateWord (Day 4) is: grid word → already found →
 * dictionary bonus → invalid. Checking the grid first means the common case
 * costs one set lookup.
 */
sealed interface WordResult {

    val word: String

    /** Matches an unrevealed word in the crossword grid. Reveals it. */
    data class GridWord(
        override val word: String,
        val wordIndex: Int,
    ) : WordResult

    /** A valid word from the dictionary, but not placed in the grid. */
    data class BonusWord(override val word: String) : WordResult

    /** Already revealed (grid) or already collected (bonus). */
    data class AlreadyFound(
        override val word: String,
        val wasBonus: Boolean,
    ) : WordResult

    /** Not a word, or too short. Drives the shake animation. */
    data class Invalid(override val word: String) : WordResult
}
