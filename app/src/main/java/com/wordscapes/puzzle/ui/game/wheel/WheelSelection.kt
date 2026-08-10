package com.wordscapes.puzzle.ui.game.wheel

import com.wordscapes.puzzle.domain.model.GameRules

/**
 * The selection state machine for the letter wheel, as pure functions.
 *
 * Deliberately separated from anything Compose or gesture related. The rules
 * below are where a swipe-to-select wheel is actually right or wrong, and
 * keeping them pure means they get real unit tests instead of being verified
 * by dragging a thumb around and hoping.
 *
 * Selection is an ordered [List] of indices into the level's letter list.
 * Order is the word: [2, 0, 3] spells letters[2] + letters[0] + letters[3].
 */
object WheelSelection {

    /**
     * Fold a single letter entry into the current selection.
     *
     * Three cases, and the second is the one that makes the wheel feel right:
     *
     * 1. **Unselected letter** → append. The ordinary case.
     *
     * 2. **The letter immediately before the current last one** → pop the last
     *    entry. This is backtracking: the player retraces along the path they
     *    just drew to undo a letter. Without it the only way to fix a mistake
     *    is to lift and start over, which is the single most common complaint
     *    about naive implementations of this control.
     *
     * 3. **Any other already-selected letter** → ignore.
     *    Two sub-cases, both must be no-ops. Re-entering the *current* last
     *    letter happens constantly, because a finger resting inside a hitbox
     *    generates a stream of move events — appending on each would produce
     *    "SSSSS". Entering a letter selected earlier in the path (crossing
     *    back over the middle of your own trail) must also be ignored, because
     *    a letter can only be used once per word.
     *
     * Returns [current] unchanged when the entry is a no-op, so callers can
     * cheaply detect "nothing happened" by reference equality.
     */
    fun apply(
        current: List<Int>,
        entered: Int,
        allowBacktrack: Boolean = true,
    ): List<Int> = when {
        current.isEmpty() -> listOf(entered)

        // Still inside the letter we are already on — extremely common, no-op.
        entered == current.last() -> current

        // Retracing onto the previous letter — undo one step.
        allowBacktrack && current.size >= 2 && entered == current[current.size - 2] ->
            current.subList(0, current.size - 1).toList()

        // Already used elsewhere in the word — cannot reuse.
        entered in current -> current

        else -> current + entered
    }

    /**
     * Fold an ordered path of letters into the current selection.
     *
     * [WheelGeometry.hitTestSegment] can report several letters for a single
     * pointer event when the finger moved fast, so entries must be applied in
     * travel order rather than only taking the last one. Taking only the last
     * is the subtle version of the skipped-letter bug: the letter is detected
     * but never appended.
     *
     * Folding also makes fast backtracking work. A quick flick back across two
     * letters produces a path like [3, 1], which pops twice — the same result
     * as two slow retrace steps.
     */
    fun applyPath(
        current: List<Int>,
        path: List<Int>,
        pointerIndex: Int,
    ): List<Int> {
        // Is this gesture a retrace, or is it drawing forward?
        //
        // The discriminator is where the finger ENDS UP. Landing on a letter
        // already in the selection means retracing back along the path just
        // drawn; landing on a new letter (or on empty space) means heading
        // somewhere else, and anything crossed on the way is incidental.
        //
        // This matters because of a bug found on device. On a five letter
        // wheel the hop from A to C spans 144 degrees with R sitting between
        // them, and the arc a finger actually draws passes through R's hit
        // circle. R being the second-to-last selection, treating that crossing
        // as a retrace popped A, so R-A-C-E silently became R-C-E.
        //
        // An earlier fix allowed backtracking only for the letter directly
        // under the finger. That killed the bug but broke retracing: flicking
        // back across two letters popped nothing at all, and did not recover
        // on subsequent samples either. Deciding once per gesture handles both
        // — a fast two letter retrace pops twice, and a forward arc through an
        // old letter pops nothing.
        val isRetracing = pointerIndex >= 0 && pointerIndex in current

        return path.fold(current) { acc, index ->
            apply(acc, index, allowBacktrack = isRetracing)
        }
    }

    /**
     * The word spelled by [selection], or the empty string.
     *
     * Indices outside [letters] are dropped rather than throwing. That should
     * be impossible — selections only ever come from hit tests against the
     * same wheel — but a crash mid-swipe is a far worse failure than a short
     * word, and this is on the hot path for every submission.
     */
    fun wordOf(selection: List<Int>, letters: List<Char>): String {
        if (selection.isEmpty()) return ""
        val sb = StringBuilder(selection.size)
        for (i in selection) {
            letters.getOrNull(i)?.let(sb::append)
        }
        return sb.toString()
    }

    /** Minimum letters before a selection is worth submitting. */
    const val MIN_WORD_LENGTH = GameRules.MIN_WORD_LENGTH

    /**
     * Whether [selection] is long enough to submit.
     *
     * Note this is *not* enforced during the drag — a two-letter selection is
     * drawn normally, it simply resolves as invalid on release if the player
     * lifts there. Blocking the third letter from being appended, or hiding
     * the line until three letters are down, both feel broken.
     */
    fun isSubmittable(selection: List<Int>): Boolean =
        selection.size >= MIN_WORD_LENGTH
}
