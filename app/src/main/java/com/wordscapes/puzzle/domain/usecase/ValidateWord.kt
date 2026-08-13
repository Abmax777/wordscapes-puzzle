package com.wordscapes.puzzle.domain.usecase

import com.wordscapes.puzzle.domain.model.GameRules
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.WordResult
import com.wordscapes.puzzle.domain.repository.WordLookup
import javax.inject.Inject

/**
 * Resolves a submitted word. Order is grid, already-found, dictionary, invalid:
 * cheapest first, and the suspending lookup last. See README.
 */
class ValidateWord @Inject constructor(
    private val wordLookup: WordLookup,
) {
    suspend operator fun invoke(
        word: String,
        level: Level,
        revealedWordIndices: Set<Int>,
        foundBonusWords: Set<String>,
    ): WordResult {
        val candidate = word.uppercase()

        if (candidate.length < GameRules.MIN_WORD_LENGTH) {
            return WordResult.Invalid(candidate)
        }
        if (!isSpellableFrom(candidate, level.letters)) {
            return WordResult.Invalid(candidate)
        }

        val gridIndex = level.words.indexOfFirst { it.word == candidate }
        if (gridIndex >= 0) {
            return if (gridIndex in revealedWordIndices) {
                WordResult.AlreadyFound(candidate, wasBonus = false)
            } else {
                WordResult.GridWord(candidate, gridIndex)
            }
        }

        if (candidate in foundBonusWords) {
            return WordResult.AlreadyFound(candidate, wasBonus = true)
        }

        return if (wordLookup.contains(candidate)) {
            WordResult.BonusWord(candidate)
        } else {
            WordResult.Invalid(candidate)
        }
    }

    /** Multiset containment. The wheel already guarantees this; the domain re-checks. */
    private fun isSpellableFrom(word: String, wheel: List<Char>): Boolean {
        val available = HashMap<Char, Int>(wheel.size)
        for (c in wheel) available[c] = (available[c] ?: 0) + 1
        for (c in word) {
            val left = available[c] ?: return false
            if (left == 0) return false
            available[c] = left - 1
        }
        return true
    }
}
