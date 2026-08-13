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
 * Bonus-word lookup. Ships only words reachable from some level's wheel — 400
 * entries rather than a full lexicon. Stored uppercase, the form the wheel emits.
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
