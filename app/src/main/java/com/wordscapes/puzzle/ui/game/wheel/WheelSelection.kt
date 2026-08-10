package com.wordscapes.puzzle.ui.game.wheel

import com.wordscapes.puzzle.domain.model.GameRules

/**
 * The selection state machine for the letter wheel, as pure functions.
 *
 * Deliberately free of Compose and gesture code. These rules are where a
 * swipe-select wheel is right or wrong, and keeping them pure means they get
 * real unit tests instead of being verified by dragging a thumb and hoping.
 *
 * Selection is an ordered [List] of indices into the level's letter list.
 * Order is the word: [2, 0, 3] spells letters[2] + letters[0] + letters[3].
 *
 * ## Two operations, not three
 *
 * Earlier revisions modelled this as append / backtrack / ignore, with
 * backtrack triggered by *entering* the second-to-last letter. Three bugs came
 * out of that framing, each fixed by a narrower condition on when a crossing
 * counted as a retrace, and each fix broke something else.
 *
 * The framing was the problem. Retracing is not "entering a particular
 * letter", it is "the finger is now resting on a letter earlier in the word" —
 * a statement about position, not about movement. Expressed that way it needs
 * no path, no adjacency check and no special cases:
 *
 *   - If the finger is inside a letter earlier in the selection, the word is
 *     truncated to end there. Distance does not matter, so a slow one-letter
 *     retrace and a fast flick back across three behave identically.
 *   - Otherwise the finger is drawing forward, and every letter the path swept
 *     is appended if new and ignored if not. Nothing is ever undone.
 */
object WheelSelection {

    /** Shortest submittable word. */
    const val MIN_WORD_LENGTH = GameRules.MIN_WORD_LENGTH

    /**
     * Add [entered] to the end if it is not already in the word.
     *
     * A letter can only be used once per word, and re-entering the letter the
     * finger is already inside happens constantly — a resting finger emits a
     * stream of move events, and appending on each would spell `SSSSS`. Both
     * cases are the same "already present, ignore" branch.
     *
     * Returns [current] unchanged when nothing happens, so callers can detect
     * "no change" by reference equality and skip a snapshot write.
     */
    fun append(current: List<Int>, entered: Int): List<Int> =
        if (entered in current) current else current + entered

    /**
     * Fold one pointer sample into the selection.
     *
     * @param path letters the segment since the last sample swept through, in
     *   travel order — see [WheelGeometry.hitTestSegment]. A fast flick can
     *   cross several letters in a single sample, so this is a list rather
     *   than one index.
     * @param pointerIndex the letter the finger is inside *right now*, or -1
     *   for empty space. This is what decides retrace versus forward, because
     *   where the finger came to rest is the only reliable signal of intent.
     */
    fun applyPath(
        current: List<Int>,
        path: List<Int>,
        pointerIndex: Int,
    ): List<Int> {
        if (pointerIndex >= 0) {
            val position = current.indexOf(pointerIndex)
            // Inside a letter earlier in the word: retrace, truncate to it.
            //
            // `position < lastIndex` is load-bearing. Resting inside the LAST
            // letter is the ordinary state of drawing forward, not a retrace.
            // Treating it as one was the bug where the second letter kept
            // dropping: adjacent hit circles overlap, so the moment a thumb
            // began leaving letter two the segment grazed letter one, and
            // letter one was the pop trigger.
            if (position in 0 until current.lastIndex) {
                return current.subList(0, position + 1).toList()
            }
        }

        // Drawing forward. Swept letters append if new; nothing is undone.
        return path.fold(current) { acc, entered -> append(acc, entered) }
    }

    /**
     * The word spelled by [selection], or the empty string.
     *
     * Indices outside [letters] are dropped rather than throwing. That should
     * be unreachable — selections only come from hit tests against the same
     * wheel — but a crash mid-swipe is far worse than a short word, and this
     * is on the hot path for every submission.
     */
    fun wordOf(selection: List<Int>, letters: List<Char>): String {
        if (selection.isEmpty()) return ""
        val sb = StringBuilder(selection.size)
        for (i in selection) {
            letters.getOrNull(i)?.let(sb::append)
        }
        return sb.toString()
    }

    /**
     * Whether [selection] is long enough to submit.
     *
     * Not enforced during the drag — a two-letter selection is drawn normally
     * and simply resolves as invalid if the player lifts there. Blocking the
     * third letter from being appended, or hiding the line until three letters
     * are down, both feel broken.
     */
    fun isSubmittable(selection: List<Int>): Boolean =
        selection.size >= MIN_WORD_LENGTH
}
