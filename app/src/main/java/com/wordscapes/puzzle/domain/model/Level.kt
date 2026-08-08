package com.wordscapes.puzzle.domain.model

/** A cell coordinate in the crossword grid. Origin is top-left, (0,0). */
data class GridPosition(val row: Int, val col: Int)

/**
 * One word placed into the crossword grid.
 *
 * [row]/[col] are the coordinates of the word's first letter; the word then
 * runs right if [horizontal], down otherwise.
 */
data class PlacedWord(
    val word: String,
    val row: Int,
    val col: Int,
    val horizontal: Boolean,
) {
    /** Coordinates this word occupies, in reading order. */
    val positions: List<GridPosition> = List(word.length) { i ->
        if (horizontal) GridPosition(row, col + i) else GridPosition(row + i, col)
    }
}

/**
 * A single occupied cell.
 *
 * [wordIndices] holds every index into [Level.words] whose word passes through
 * this cell — size 1 for a normal cell, 2 at an intersection. The gameplay
 * layer needs this to decide whether revealing one word should also reveal a
 * shared letter of a word still hidden.
 */
data class GridCell(
    val position: GridPosition,
    val letter: Char,
    val wordIndices: Set<Int>,
) {
    val isIntersection: Boolean get() = wordIndices.size > 1
}

/**
 * A fully validated, ready-to-play level.
 *
 * [cells] is *derived* at parse time rather than stored in JSON. That is
 * deliberate: deriving it forces every placement to be checked against every
 * other placement while loading, so a malformed level throws
 * [LevelFormatException] at startup instead of producing an unwinnable grid
 * that only reveals itself three words into play.
 */
data class Level(
    val id: Int,
    val letters: List<Char>,
    val gridWidth: Int,
    val gridHeight: Int,
    val words: List<PlacedWord>,
    val cells: Map<GridPosition, GridCell>,
) {
    /** Every word in this level's grid, uppercase. Used by validation. */
    val gridWords: Set<String> = words.mapTo(mutableSetOf()) { it.word }

    fun cellAt(row: Int, col: Int): GridCell? = cells[GridPosition(row, col)]
}

/**
 * Thrown when a level in `levels.json` is structurally invalid.
 *
 * Failing loudly here is the whole point of deriving the grid at load time.
 */
class LevelFormatException(message: String) : IllegalStateException(message)
