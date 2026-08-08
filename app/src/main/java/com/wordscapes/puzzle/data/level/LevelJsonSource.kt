package com.wordscapes.puzzle.data.level

import android.content.Context
import com.wordscapes.puzzle.di.IoDispatcher
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.LevelFormatException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and parses `assets/levels.json`.
 *
 * Asset reads are blocking file I/O, so everything runs on the injected
 * dispatcher rather than whichever thread happened to call in. The dispatcher
 * is a constructor parameter purely so unit tests can pass an immediate one —
 * this is the ordinary way to keep a suspend function testable without a
 * scheduler.
 */
@Singleton
class LevelJsonSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        ignoreUnknownKeys = true   // tolerate generator debug fields
        isLenient = false          // but not malformed JSON
    }

    suspend fun loadLevels(): List<Level> = withContext(ioDispatcher) {
        val raw = try {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            throw LevelFormatException("could not read assets/$ASSET_NAME: ${e.message}")
        }

        val parsed = try {
            json.decodeFromString<LevelsFileDto>(raw)
        } catch (e: Exception) {
            throw LevelFormatException("assets/$ASSET_NAME is not valid level JSON: ${e.message}")
        }

        if (parsed.levels.isEmpty()) {
            throw LevelFormatException("assets/$ASSET_NAME contains no levels")
        }

        val levels = parsed.levels.map(LevelMapper::toDomain)

        levels.groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .firstOrNull()
            ?.let { throw LevelFormatException("duplicate level id $it") }

        levels.sortedBy { it.id }
    }

    private companion object {
        const val ASSET_NAME = "levels.json"
    }
}
