package com.wordscapes.puzzle.domain.model

/** Rules the UI and domain must agree on. In domain/ so dependencies point inwards. */
object GameRules {

    /** Checked by the wheel before emitting and again by ValidateWord, which
     *  cannot assume its caller was the wheel. */
    const val MIN_WORD_LENGTH = 3
}
