package com.wordscapes.puzzle.domain.usecase

import com.wordscapes.puzzle.domain.model.GameRules
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.WordResult
import com.wordscapes.puzzle.domain.repository.WordLookup
import javax.inject.Inject

/**
 * Resolves a submitted word into one of the four [WordResult] outcomes.
 *
 * ## Resolution order
 *
 * Grid word, then already-found, then dictionary bonus, then invalid. The
 * order is not arbitrary:
 *
 * - The grid is checked first because it is the common case and the cheapest
 *   check — a handful of string comparisons against words already in memory,
 *   with no suspending dictionary lookup at all.
 *
 * - Already-found is checked before the dictionary so that re-submitting a
 *   word you have already found reports [WordResult.AlreadyFound] rather than
 *   silently re-reporting it as a fresh bonus and inflating the count.
 *
 * - The dictionary is last because it is the only suspending call.
 *
 * ## Why the wheel check exists
 *
 * `dictionary.txt` is shared across all fifteen levels, so it contains words
 * reachable from *some* wheel, not necessarily this one. In practice the wheel
 * can only emit letters it displays, so an unspellable word cannot arrive — but
 * that is a UI guarantee, and the domain should not depend on the UI upholding
 * it. Enforcing it here means the rule holds no matter what calls this.
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

    /** Multiset containment: every letter used no more often than the wheel offers. */
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
