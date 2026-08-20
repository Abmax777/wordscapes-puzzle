package com.wordscapes.puzzle.domain.repository

/**
 * Is this a real word? An interface so the domain neither needs an Android
 * Context to be tested nor knows the lookup is a file in assets.
 */
interface WordLookup {
    /** Case-insensitive. Does not check whether the word is spellable. */
    suspend fun contains(word: String): Boolean
}
