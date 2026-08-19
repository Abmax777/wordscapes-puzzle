package com.wordscapes.puzzle.domain.repository

import com.wordscapes.puzzle.domain.model.Level

/** Read-only level content. An interface so ViewModels test on the JVM. */
interface LevelCatalog {
    suspend fun getLevels(): List<Level>
    suspend fun getLevel(id: Int): Level?
    suspend fun levelCount(): Int

    /** The next level's id, or null if [id] is the last one. */
    suspend fun nextLevelId(id: Int): Int?
}
