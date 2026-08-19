package com.wordscapes.puzzle.data.dictionary

import com.wordscapes.puzzle.domain.repository.WordLookup
import com.wordscapes.puzzle.resources.Res
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Bonus-word lookup. Ships only words reachable from some level's wheel — 400
 * entries rather than a full lexicon. Stored uppercase, the form the wheel emits.
 */
class DictionarySource(
    private val ioDispatcher: CoroutineDispatcher,
) : WordLookup {
    private val mutex = Mutex()
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

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun load(): Set<String> = withContext(ioDispatcher) {
        Res.readBytes(RESOURCE).decodeToString()
            .lineSequence()
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private companion object {
        const val RESOURCE = "files/dictionary.txt"
    }
}
