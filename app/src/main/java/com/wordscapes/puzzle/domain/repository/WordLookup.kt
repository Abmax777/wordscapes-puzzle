package com.wordscapes.puzzle.domain.repository

/**
 * Answers "is this a real word?".
 *
 * An interface in the domain layer rather than a direct dependency on
 * DictionarySource, for two reasons.
 *
 * First, testability: DictionarySource needs an Android Context to read
 * assets, so a use case depending on it directly could only be tested on a
 * device or under Robolectric. Depending on this interface means ValidateWord
 * gets ordinary JVM unit tests with a two-line fake.
 *
 * Second, direction: the domain layer should not know that word lookup happens
 * to be backed by a text file in assets. Today it is; if it became a trie, a
 * Room table or a bundled binary, nothing in domain/ would change.
 */
interface WordLookup {
    /** Case-insensitive. Does not check whether the word is spellable. */
    suspend fun contains(word: String): Boolean
}
