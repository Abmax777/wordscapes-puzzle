package com.wordscapes.puzzle.data.level

import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.repository.LevelCatalog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for level content.
 *
 * Levels are immutable and small (15 of them, 16 KB of JSON), so they are
 * parsed once and cached for the process lifetime. The [Mutex] makes the
 * first-load path safe when two screens ask concurrently — without it, a
 * LevelSelect prefetch racing a Game screen's load would parse the file twice.
 * Not a correctness bug, but wasteful and a real source of jank on the
 * transition into gameplay.
 */
@Singleton
class LevelRepository @Inject constructor(
    private val source: LevelJsonSource,
) : LevelCatalog {
    private val mutex = Mutex()

    @Volatile
    private var cached: List<Level>? = null

    override suspend fun getLevels(): List<Level> {
        cached?.let { return it }
        return mutex.withLock {
            // Re-check: another caller may have populated it while we waited.
            cached ?: source.loadLevels().also { cached = it }
        }
    }

    override suspend fun getLevel(id: Int): Level? = getLevels().firstOrNull { it.id == id }

    override suspend fun levelCount(): Int = getLevels().size

    /** The next level's id, or null if [id] is the last one. */
    override suspend fun nextLevelId(id: Int): Int? {
        val levels = getLevels()
        val index = levels.indexOfFirst { it.id == id }
        return if (index == -1 || index == levels.lastIndex) null else levels[index + 1].id
    }
}
