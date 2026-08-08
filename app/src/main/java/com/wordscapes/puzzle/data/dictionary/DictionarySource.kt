package com.wordscapes.puzzle.data.dictionary

import android.content.Context
import com.wordscapes.puzzle.di.IoDispatcher
import com.wordscapes.puzzle.domain.repository.WordLookup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bonus-word lookup, backed by `assets/dictionary.txt`.
 *
 * The generator ships only words that are sub-anagrams of at least one level's
 * wheel — 444 entries, ~4 KB — rather than a full English lexicon. A word the
 * player cannot possibly spell never needs to be looked up, so shipping it
 * would cost APK size and load time for nothing.
 *
 * Stored uppercase because that is the form the wheel emits; lowercasing on
 * every submission instead would allocate a string per swipe.
 */
@Singleton
class DictionarySource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WordLookup {
    private val mutex = Mutex()

    @Volatile
    private var words: Set<String>? = null

    suspend fun words(): Set<String> {
        words?.let { return it }
        return mutex.withLock {
            words ?: load().also { words = it }
        }
    }

    /** True if [word] is a real word. Case-insensitive; does not check the wheel. */
    override suspend fun contains(word: String): Boolean =
        words().contains(word.uppercase())

    private suspend fun load(): Set<String> = withContext(ioDispatcher) {
        context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
            lines.map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    }

    private companion object {
        const val ASSET_NAME = "dictionary.txt"
    }
}
