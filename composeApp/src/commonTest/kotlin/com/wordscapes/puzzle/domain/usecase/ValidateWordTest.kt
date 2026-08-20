package com.wordscapes.puzzle.domain.usecase

import com.wordscapes.puzzle.data.level.LevelDto
import com.wordscapes.puzzle.data.level.LevelMapper
import com.wordscapes.puzzle.data.level.PlacedWordDto
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.WordResult
import com.wordscapes.puzzle.domain.repository.WordLookup
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Exercises every branch of the resolution order, including the ones that only
 * differ by which of two similar outcomes is returned — those are the ones a
 * refactor silently breaks.
 */
class ValidateWordTest {

    /** Two lines, no Android, no Robolectric. The whole point of the interface. */
    private class FakeLookup(private val words: Set<String>) : WordLookup {
        override suspend fun contains(word: String) = word.uppercase() in words
    }

    /**
     * Wheel STARE. Grid holds STARE across and RATE down, sharing the R.
     * TEARS is a valid word from the same letters but is NOT in the grid, so
     * it is the bonus case.
     */
    private val level: Level = LevelMapper.toDomain(
        LevelDto(
            id = 1,
            letters = listOf("S", "T", "A", "R", "E"),
            gridWidth = 5,
            gridHeight = 5,
            words = listOf(
                PlacedWordDto("STARE", 0, 0, true),
                PlacedWordDto("RATE", 0, 3, false),
            ),
        ),
    )

    private val lookup = FakeLookup(setOf("STARE", "RATE", "TEARS", "EAST", "ARTS", "SEA"))
    private val validate = ValidateWord(lookup)

    // ── Grid words ───────────────────────────────────────────────────────────

    @Test
    fun `an unrevealed grid word resolves to GridWord with its index`() = runTest {
        val r = validate("STARE", level, emptySet(), emptySet())
        assertTrue(r is WordResult.GridWord, "got $r")
        assertEquals(0, (r as WordResult.GridWord).wordIndex)
    }

    @Test
    fun `the second grid word carries its own index`() = runTest {
        val r = validate("RATE", level, emptySet(), emptySet())
        assertEquals(1, (r as WordResult.GridWord).wordIndex)
    }

    @Test
    fun `input case does not matter`() = runTest {
        assertTrue(validate("stare", level, emptySet(), emptySet()) is WordResult.GridWord)
    }

    // ── Already found ────────────────────────────────────────────────────────

    @Test
    fun `a revealed grid word resolves to AlreadyFound, not GridWord`() = runTest {
        val r = validate("STARE", level, revealedWordIndices = setOf(0), emptySet())
        assertTrue(r is WordResult.AlreadyFound, "got $r")
        assertFalse((r as WordResult.AlreadyFound).wasBonus)
    }

    @Test
    fun `a repeated bonus word resolves to AlreadyFound and is flagged as bonus`() = runTest {
        val r = validate("TEARS", level, emptySet(), foundBonusWords = setOf("TEARS"))
        assertTrue(r is WordResult.AlreadyFound, "got $r")
        assertTrue((r as WordResult.AlreadyFound).wasBonus)
    }

    @Test
    fun `revealing one word does not affect the other`() = runTest {
        val r = validate("RATE", level, revealedWordIndices = setOf(0), emptySet())
        assertTrue(r is WordResult.GridWord, "got $r")
    }

    // ── Bonus words ──────────────────────────────────────────────────────────

    @Test
    fun `a dictionary word absent from the grid is a bonus word`() = runTest {
        val r = validate("TEARS", level, emptySet(), emptySet())
        assertTrue(r is WordResult.BonusWord, "got $r")
    }

    @Test
    fun `grid membership beats dictionary membership`() = runTest {
        // STARE is in both. It must resolve as a grid word, never as bonus.
        assertTrue(validate("STARE", level, emptySet(), emptySet()) is WordResult.GridWord)
    }

    // ── Invalid ──────────────────────────────────────────────────────────────

    @Test
    fun `a non-word is invalid`() = runTest {
        assertTrue(validate("TSR", level, emptySet(), emptySet()) is WordResult.Invalid)
    }

    @Test
    fun `words shorter than the minimum are invalid`() = runTest {
        assertTrue(validate("AT", level, emptySet(), emptySet()) is WordResult.Invalid)
        assertTrue(validate("", level, emptySet(), emptySet()) is WordResult.Invalid)
    }

    @Test
    fun `a real word not spellable from this wheel is invalid`() = runTest {
        // dictionary.txt is shared across all levels, so it contains words
        // reachable from other wheels. The wheel cannot emit them, but the
        // domain must not rely on the UI for that.
        // Control: SEA *is* spellable from STARE, so it must still resolve.
        val r = validate("SEA", level, emptySet(), emptySet())
        assertTrue(r is WordResult.BonusWord, "got $r")

        // QUIZ is in no wheel here and uses letters STARE does not have.

        val quiz = validate("QUIZ", level, emptySet(), emptySet())
        assertTrue(quiz is WordResult.Invalid, "got $quiz")
    }

    @Test
    fun `a letter cannot be used more often than the wheel offers`() = runTest {
        // STARE has one S. A word needing two must not validate even if the
        // fake lookup would accept it.
        val doubled = ValidateWord(FakeLookup(setOf("STARES", "SASS")))
        assertTrue(doubled("SASS", level, emptySet(), emptySet()) is WordResult.Invalid)
    }
}
