package com.wordscapes.puzzle.data.level

import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.LevelFormatException
import com.wordscapes.puzzle.resources.Res
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Reads and parses the bundled level JSON. The dispatcher is injected so tests
 * can pass an immediate one.
 */
class LevelJsonSource(
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        ignoreUnknownKeys = true   // generator debug fields
        isLenient = false          // but not malformed JSON
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadLevels(): List<Level> = withContext(ioDispatcher) {
        val raw = try {
            Res.readBytes(RESOURCE).decodeToString()
        } catch (e: Exception) {
            throw LevelFormatException("could not read $RESOURCE: ${e.message}")
        }

        val parsed = try {
            json.decodeFromString<LevelsFileDto>(raw)
        } catch (e: Exception) {
            throw LevelFormatException("$RESOURCE is not valid level JSON: ${e.message}")
        }

        if (parsed.levels.isEmpty()) {
            throw LevelFormatException("$RESOURCE contains no levels")
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
        const val RESOURCE = "files/levels.json"
    }
}
