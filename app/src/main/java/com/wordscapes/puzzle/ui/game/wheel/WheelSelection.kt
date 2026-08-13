package com.wordscapes.puzzle.ui.game.wheel

import com.wordscapes.puzzle.domain.model.GameRules

/**
 * Selection rules for the wheel, as pure functions. Selection is an ordered list
 * of indices into the level's letters; order is the word.
 */
object WheelSelection {

    const val MIN_WORD_LENGTH = GameRules.MIN_WORD_LENGTH

    /** Append unless already present. A letter is used once per word. */
    fun append(current: List<Int>, entered: Int): List<Int> =
        if (entered in current) current else current + entered

    /**
     * Fold one pointer sample in: [path] is what the segment crossed in travel
     * order, [pointerIndex] the letter the finger rests inside now, or -1.
     */
    fun applyPath(
        current: List<Int>,
        path: List<Int>,
        pointerIndex: Int,
    ): List<Int> {
        if (pointerIndex >= 0) {
            val position = current.indexOf(pointerIndex)
            // Resting on the LAST letter is ordinary forward drawing, not a retrace.
            if (position in 0 until current.lastIndex) {
                return current.subList(0, position + 1).toList()
            }
        }
        return path.fold(current) { acc, entered -> append(acc, entered) }
    }

    /** Out-of-range indices are dropped; a crash mid-swipe is worse than a short word. */
    fun wordOf(selection: List<Int>, letters: List<Char>): String {
        if (selection.isEmpty()) return ""
        val sb = StringBuilder(selection.size)
        for (i in selection) letters.getOrNull(i)?.let(sb::append)
        return sb.toString()
    }

    /** Gates submission only. Short selections still draw normally during the drag. */
    fun isSubmittable(selection: List<Int>): Boolean =
        selection.size >= MIN_WORD_LENGTH
}
