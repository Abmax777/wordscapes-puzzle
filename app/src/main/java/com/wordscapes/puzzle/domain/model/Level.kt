package com.wordscapes.puzzle.domain.model

import androidx.compose.runtime.Immutable

// @Immutable: androidx.compose.runtime is a contract library, not UI, and is
// multiplatform. Without it every composable taking a Level is non-skippable.

/** A cell coordinate in the crossword grid. Origin is top-left, (0,0). */
@Immutable
data class GridPosition(val row: Int, val col: Int)

/** A word placed in the grid. [row]/[col] is the first letter; runs right if [horizontal]. */
@Immutable
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
 * One occupied cell. [wordIndices] holds every word through it — size 2 at an
 * intersection, which is how revealing one word fills another's shared letters.
 */
@Immutable
data class GridCell(
    val position: GridPosition,
    val letter: Char,
    val wordIndices: Set<Int>,
) {
    val isIntersection: Boolean get() = wordIndices.size > 1
}

/**
 * A validated level. [cells] is derived at parse time, not stored, so a malformed
 * placement throws [LevelFormatException] at load rather than mid-level.
 */
@Immutable
data class Level(
    val id: Int,
    val letters: List<Char>,
    val gridWidth: Int,
    val gridHeight: Int,
    val words: List<PlacedWord>,
    val cells: Map<GridPosition, GridCell>,
) {
    val gridWords: Set<String> = words.mapTo(mutableSetOf()) { it.word }

    fun cellAt(row: Int, col: Int): GridCell? = cells[GridPosition(row, col)]
}

/** Thrown when a level in `levels.json` is structurally invalid. */
class LevelFormatException(message: String) : IllegalStateException(message)
