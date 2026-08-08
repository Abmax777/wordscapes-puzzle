package com.wordscapes.puzzle.data.level

import com.wordscapes.puzzle.domain.model.GridCell
import com.wordscapes.puzzle.domain.model.GridPosition
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.LevelFormatException
import com.wordscapes.puzzle.domain.model.PlacedWord

/**
 * DTO → domain, with validation.
 *
 * Every check below exists because the failure it catches is silent otherwise:
 * a word that escapes the grid draws off-screen, a letter conflict at an
 * intersection makes a level unwinnable, and a word that isn't spellable from
 * the wheel simply can never be entered. All three look like gameplay bugs
 * days later. Catching them at load turns them into a stack trace on launch
 * with the offending level id in the message.
 *
 * This is the app-side mirror of `tools/validate_levels.py`. The generator is
 * a throwaway build tool and is not on the device; this is the check that
 * actually ships.
 */
object LevelMapper {

    private const val MIN_WHEEL_LETTERS = 3
    private const val MAX_WHEEL_LETTERS = 8

    fun toDomain(dto: LevelDto): Level {
        val letters = dto.letters.map { token ->
            if (token.length != 1) {
                throw LevelFormatException(
                    "level ${dto.id}: letter token '$token' must be exactly one character",
                )
            }
            token[0].uppercaseChar()
        }

        if (letters.size !in MIN_WHEEL_LETTERS..MAX_WHEEL_LETTERS) {
            throw LevelFormatException(
                "level ${dto.id}: wheel has ${letters.size} letters, expected " +
                    "$MIN_WHEEL_LETTERS..$MAX_WHEEL_LETTERS",
            )
        }
        if (dto.gridWidth <= 0 || dto.gridHeight <= 0) {
            throw LevelFormatException(
                "level ${dto.id}: grid is ${dto.gridWidth}x${dto.gridHeight}",
            )
        }
        if (dto.words.isEmpty()) {
            throw LevelFormatException("level ${dto.id}: has no words")
        }

        val words = dto.words.map { w ->
            PlacedWord(
                word = w.word.uppercase(),
                row = w.row,
                col = w.col,
                horizontal = w.horizontal,
            )
        }

        // Duplicate words would let one swipe reveal two entries at once.
        words.groupBy { it.word }
            .filterValues { it.size > 1 }
            .keys
            .firstOrNull()
            ?.let { throw LevelFormatException("level ${dto.id}: '$it' placed twice") }

        val wheelCounts: Map<Char, Int> = letters.groupingBy { it }.eachCount()
        val cells = HashMap<GridPosition, GridCell>()

        words.forEachIndexed { index, placed ->
            // Spellable from the wheel?
            val needed = placed.word.groupingBy { it }.eachCount()
            needed.forEach { (ch, count) ->
                val available = wheelCounts[ch] ?: 0
                if (count > available) {
                    throw LevelFormatException(
                        "level ${dto.id}: '${placed.word}' needs $count x '$ch' but the " +
                            "wheel (${letters.joinToString("")}) has $available",
                    )
                }
            }

            placed.positions.forEachIndexed { i, pos ->
                // Inside the declared grid?
                if (pos.row !in 0 until dto.gridHeight || pos.col !in 0 until dto.gridWidth) {
                    throw LevelFormatException(
                        "level ${dto.id}: '${placed.word}' reaches (${pos.row},${pos.col}), " +
                            "outside ${dto.gridWidth}x${dto.gridHeight}",
                    )
                }

                val letter = placed.word[i]
                val existing = cells[pos]
                if (existing == null) {
                    cells[pos] = GridCell(pos, letter, setOf(index))
                } else {
                    // Intersections must agree on their letter.
                    if (existing.letter != letter) {
                        throw LevelFormatException(
                            "level ${dto.id}: conflict at (${pos.row},${pos.col}) — " +
                                "'${existing.letter}' vs '$letter' from '${placed.word}'",
                        )
                    }
                    cells[pos] = existing.copy(wordIndices = existing.wordIndices + index)
                }
            }
        }

        return Level(
            id = dto.id,
            letters = letters,
            gridWidth = dto.gridWidth,
            gridHeight = dto.gridHeight,
            words = words,
            cells = cells,
        )
    }
}
