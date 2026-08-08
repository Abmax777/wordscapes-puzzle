package com.wordscapes.puzzle.data

import com.wordscapes.puzzle.data.level.LevelDto
import com.wordscapes.puzzle.data.level.LevelMapper
import com.wordscapes.puzzle.data.level.LevelsFileDto
import com.wordscapes.puzzle.data.level.PlacedWordDto
import com.wordscapes.puzzle.domain.model.GridPosition
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.LevelFormatException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Verifies the shipped level assets against the same rules as
 * `tools/validate_levels.py`. Two independent implementations of the same
 * contract — if the generator and the app ever disagree about what a valid
 * level is, one of these fails.
 *
 * Runs as a plain JVM test (no Robolectric, no emulator), so it reads the
 * asset off disk rather than through Context.assets. Gradle sets the working
 * directory to the module root for Test tasks, hence the relative path.
 */
class LevelDataTest {

    companion object {
        private lateinit var levels: List<Level>
        private lateinit var dictionary: Set<String>

        private fun resolve(relative: String): File {
            // Tolerate being run from either the module dir or the repo root.
            val candidates = listOf(
                File(relative),
                File("app/$relative"),
                File("../app/$relative"),
            )
            return candidates.firstOrNull { it.exists() }
                ?: error(
                    "could not locate $relative from ${File(".").absolutePath}; " +
                        "tried ${candidates.joinToString { it.path }}",
                )
        }

        @BeforeClass
        @JvmStatic
        fun loadAssets() {
            val json = Json { ignoreUnknownKeys = true }
            val raw = resolve("src/main/assets/levels.json").readText()
            val parsed = json.decodeFromString<LevelsFileDto>(raw)
            levels = parsed.levels.map(LevelMapper::toDomain)

            dictionary = resolve("src/main/assets/dictionary.txt")
                .readLines()
                .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.uppercase() }
                .toSet()
        }
    }

    // ── The headline Day 1 exit criterion ────────────────────────────────────

    @Test
    fun `all levels parse into domain models`() {
        assertTrue("expected at least one level", levels.isNotEmpty())
        assertTrue(
            "brief requires 10-20 levels, found ${levels.size}",
            levels.size in 10..20,
        )
    }

    @Test
    fun `level ids are unique and ascending`() {
        val ids = levels.map { it.id }
        assertEquals("ids must be unique", ids.size, ids.toSet().size)
        assertEquals("levels must be sorted by id", ids.sorted(), ids)
    }

    // ── Per-level structural invariants ──────────────────────────────────────

    @Test
    fun `every grid word is spellable from its wheel`() {
        levels.forEach { level ->
            val wheel = level.letters.groupingBy { it }.eachCount()
            level.words.forEach { placed ->
                placed.word.groupingBy { it }.eachCount().forEach { (ch, need) ->
                    val have = wheel[ch] ?: 0
                    assertTrue(
                        "level ${level.id}: '${placed.word}' needs $need x '$ch', " +
                            "wheel ${level.letters.joinToString("")} has $have",
                        need <= have,
                    )
                }
            }
        }
    }

    @Test
    fun `every placement stays inside the declared grid`() {
        levels.forEach { level ->
            level.words.forEach { placed ->
                placed.positions.forEach { pos ->
                    assertTrue(
                        "level ${level.id}: '${placed.word}' reaches " +
                            "(${pos.row},${pos.col}) outside " +
                            "${level.gridWidth}x${level.gridHeight}",
                        pos.row in 0 until level.gridHeight &&
                            pos.col in 0 until level.gridWidth,
                    )
                }
            }
        }
    }

    @Test
    fun `declared dimensions are tight, not padded`() {
        levels.forEach { level ->
            val maxRow = level.cells.keys.maxOf { it.row }
            val maxCol = level.cells.keys.maxOf { it.col }
            assertEquals(
                "level ${level.id}: declared height ${level.gridHeight} " +
                    "but content ends at row $maxRow",
                level.gridHeight - 1, maxRow,
            )
            assertEquals(
                "level ${level.id}: declared width ${level.gridWidth} " +
                    "but content ends at col $maxCol",
                level.gridWidth - 1, maxCol,
            )
        }
    }

    @Test
    fun `intersections are recorded with both owning words`() {
        levels.forEach { level ->
            val intersections = level.cells.values.count { it.isIntersection }
            assertTrue(
                "level ${level.id}: a multi-word grid must have at least one " +
                    "intersection, found none",
                level.words.size < 2 || intersections > 0,
            )
            level.cells.values.forEach { cell ->
                cell.wordIndices.forEach { idx ->
                    val word = level.words[idx]
                    assertTrue(
                        "level ${level.id}: cell ${cell.position} claims word " +
                            "'${word.word}' but that word does not pass through it",
                        cell.position in word.positions,
                    )
                }
            }
        }
    }

    /**
     * The subtle one. Two words placed alongside each other are individually
     * legal but create a letter run in the perpendicular direction that spells
     * something the player can never enter — the grid looks solvable and isn't.
     */
    @Test
    fun `no unintended letter runs`() {
        levels.forEach { level ->
            val declared = level.gridWords
            listOf(0 to 1, 1 to 0).forEach { (dr, dc) ->
                level.cells.keys.forEach { pos ->
                    val behind = GridPosition(pos.row - dr, pos.col - dc)
                    if (behind in level.cells) return@forEach   // not a run start

                    val run = buildString {
                        var p = pos
                        while (p in level.cells) {
                            append(level.cells.getValue(p).letter)
                            p = GridPosition(p.row + dr, p.col + dc)
                        }
                    }
                    if (run.length > 1) {
                        assertTrue(
                            "level ${level.id}: unintended run '$run' starting at " +
                                "(${pos.row},${pos.col}) is not a declared word",
                            run in declared,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `grid is a single connected crossword`() {
        levels.forEach { level ->
            val cells = level.cells.keys
            val seen = mutableSetOf(cells.first())
            val stack = ArrayDeque(seen)
            while (stack.isNotEmpty()) {
                val (r, c) = stack.removeLast()
                listOf(
                    GridPosition(r + 1, c), GridPosition(r - 1, c),
                    GridPosition(r, c + 1), GridPosition(r, c - 1),
                ).forEach { nb ->
                    if (nb in cells && seen.add(nb)) stack.addLast(nb)
                }
            }
            assertEquals(
                "level ${level.id}: grid has disconnected islands",
                cells.size, seen.size,
            )
        }
    }

    // ── Dictionary ───────────────────────────────────────────────────────────

    @Test
    fun `every grid word resolves in the shipped dictionary`() {
        levels.forEach { level ->
            level.gridWords.forEach { word ->
                assertTrue(
                    "level ${level.id}: '$word' is in the grid but missing from " +
                        "dictionary.txt",
                    word in dictionary,
                )
            }
        }
    }

    @Test
    fun `every level has bonus words available`() {
        levels.forEach { level ->
            val wheel = level.letters.groupingBy { it }.eachCount()
            val reachable = dictionary.filter { candidate ->
                candidate.length >= 3 &&
                    candidate.groupingBy { it }.eachCount()
                        .all { (ch, n) -> n <= (wheel[ch] ?: 0) }
            }
            val bonus = reachable.toSet() - level.gridWords
            assertTrue(
                "level ${level.id}: no bonus words reachable — the bonus-word " +
                    "feedback path can never fire on this level",
                bonus.isNotEmpty(),
            )
        }
    }

    // ── Negative tests: prove the validation actually rejects bad input ──────

    private fun dtoOf(vararg words: PlacedWordDto, letters: String = "STARE") =
        LevelDto(
            id = 99,
            letters = letters.map(Char::toString),
            gridWidth = 8,
            gridHeight = 8,
            words = words.toList(),
        )

    private inline fun expectFormatError(what: String, block: () -> Unit) {
        try {
            block()
            fail("expected LevelFormatException for $what, but parsing succeeded")
        } catch (e: LevelFormatException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `rejects a word not spellable from the wheel`() {
        expectFormatError("word using a letter absent from the wheel") {
            LevelMapper.toDomain(
                dtoOf(PlacedWordDto("QUIZ", 0, 0, true)),
            )
        }
    }

    @Test
    fun `rejects a word that escapes the grid`() {
        expectFormatError("word running past the right edge") {
            LevelMapper.toDomain(
                LevelDto(
                    id = 99,
                    letters = "STARE".map(Char::toString),
                    gridWidth = 3,
                    gridHeight = 3,
                    words = listOf(PlacedWordDto("STARE", 0, 0, true)),
                ),
            )
        }
    }

    @Test
    fun `rejects conflicting letters at an intersection`() {
        expectFormatError("two words disagreeing at a shared cell") {
            LevelMapper.toDomain(
                dtoOf(
                    PlacedWordDto("STARE", 0, 0, true),   // S at (0,0)
                    PlacedWordDto("TEARS", 0, 0, false),  // T at (0,0) — conflict
                ),
            )
        }
    }

    @Test
    fun `rejects a duplicate word`() {
        expectFormatError("the same word placed twice") {
            LevelMapper.toDomain(
                dtoOf(
                    PlacedWordDto("RATE", 0, 0, true),
                    PlacedWordDto("RATE", 2, 0, true),
                ),
            )
        }
    }

    @Test
    fun `rejects an empty level`() {
        expectFormatError("a level with no words") {
            LevelMapper.toDomain(dtoOf())
        }
    }

    @Test
    fun `rejects a multi-character letter token`() {
        expectFormatError("a two-character wheel token") {
            LevelMapper.toDomain(
                LevelDto(
                    id = 99,
                    letters = listOf("S", "TA", "R", "E", "T"),
                    gridWidth = 8,
                    gridHeight = 8,
                    words = listOf(PlacedWordDto("RATE", 0, 0, true)),
                ),
            )
        }
    }
}
