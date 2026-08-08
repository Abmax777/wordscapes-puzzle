package com.wordscapes.puzzle.domain.repository

import com.wordscapes.puzzle.domain.model.Level

/**
 * Read-only access to level content.
 *
 * Same reasoning as WordLookup: the concrete LevelRepository reaches assets
 * through an Android Context, so a ViewModel depending on it directly could
 * only be tested on a device. Depending on this interface means GameViewModel
 * gets ordinary JVM tests against an in-memory fake.
 */
interface LevelCatalog {
    suspend fun getLevels(): List<Level>
    suspend fun getLevel(id: Int): Level?
    suspend fun levelCount(): Int

    /** The next level's id, or null if [id] is the last one. */
    suspend fun nextLevelId(id: Int): Int?
}
