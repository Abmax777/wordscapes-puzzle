package com.wordscapes.puzzle.domain.model

/**
 * Rules that both the UI and the domain need to agree on.
 *
 * This lives in domain/ because dependencies must point inwards: ui/ may
 * reference domain/, never the reverse. An earlier draft had ValidateWord
 * importing the minimum length from the wheel package, which inverts that and
 * would have made the domain layer unusable without the UI.
 */
object GameRules {

    /**
     * Shortest submittable word.
     *
     * Enforced in two places by design. The wheel uses it to decide whether a
     * released selection is worth emitting at all; ValidateWord re-checks it
     * because the domain cannot assume its caller was the wheel.
     */
    const val MIN_WORD_LENGTH = 3
}
