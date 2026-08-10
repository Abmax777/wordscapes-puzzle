package com.wordscapes.puzzle.ui.game.wheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These cover the selection rules that are otherwise only verifiable by
 * dragging a thumb around and watching. Several of them correspond directly to
 * the hand-test cases on the Day 2-3 list — retracing past the start, holding
 * still inside a letter, fast backtracking.
 */
class WheelSelectionTest {

    private val letters = listOf('S', 'T', 'A', 'R', 'E')

    // ── Appending ────────────────────────────────────────────────────────────

    @Test
    fun `first letter starts the selection`() {
        assertEquals(listOf(2), WheelSelection.apply(emptyList(), 2))
    }

    @Test
    fun `unselected letters append in order`() {
        var s = emptyList<Int>()
        listOf(3, 0, 4).forEach { s = WheelSelection.apply(s, it) }
        assertEquals(listOf(3, 0, 4), s)
    }

    // ── Holding still: the most frequent event by far ────────────────────────

    @Test
    fun `re-entering the current letter is a no-op`() {
        val s = listOf(1, 2)
        assertEquals(s, WheelSelection.apply(s, 2))
    }

    @Test
    fun `holding inside a letter does not repeat it`() {
        // A finger resting in a hitbox emits a stream of identical move events.
        var s = listOf(0)
        repeat(50) { s = WheelSelection.apply(s, 0) }
        assertEquals("holding still must not spell SSSS...", listOf(0), s)
    }

    @Test
    fun `no-op returns the same instance`() {
        val s = listOf(1, 2)
        assertSame(
            "callers detect 'nothing changed' by identity; do not copy on no-op",
            s, WheelSelection.apply(s, 2),
        )
    }

    // ── Backtracking ─────────────────────────────────────────────────────────

    @Test
    fun `entering the previous letter pops the last`() {
        assertEquals(listOf(0, 1), WheelSelection.apply(listOf(0, 1, 2), 1))
    }

    @Test
    fun `backtracking repeatedly unwinds the whole word`() {
        var s = listOf(0, 1, 2, 3)
        s = WheelSelection.apply(s, 2)
        assertEquals(listOf(0, 1, 2), s)
        s = WheelSelection.apply(s, 1)
        assertEquals(listOf(0, 1), s)
        s = WheelSelection.apply(s, 0)
        assertEquals(listOf(0), s)
    }

    @Test
    fun `backtracking to the start leaves one letter, not zero`() {
        // "Retrace past the start letter" from the hand-test list. Dragging
        // back onto the only selected letter must not empty the selection —
        // the finger is still down on it.
        val s = WheelSelection.apply(listOf(0), 0)
        assertEquals(listOf(0), s)
    }

    @Test
    fun `backtracking then forward again re-appends`() {
        var s = listOf(0, 1, 2)
        s = WheelSelection.apply(s, 1)      // pop -> [0,1]
        s = WheelSelection.apply(s, 2)      // append again -> [0,1,2]
        assertEquals(listOf(0, 1, 2), s)
    }

    // ── Reuse ────────────────────────────────────────────────────────────────

    @Test
    fun `crossing back over an earlier letter is ignored`() {
        // [0,1,2,3], finger crosses the middle of its own trail at index 0.
        // 0 is selected but is not the second-to-last, so it must be ignored,
        // not popped and not appended.
        val s = listOf(0, 1, 2, 3)
        assertEquals(s, WheelSelection.apply(s, 0))
    }

    @Test
    fun `a letter is never used twice in one word`() {
        var s = emptyList<Int>()
        listOf(0, 1, 2, 0, 1, 3).forEach { s = WheelSelection.apply(s, it) }
        assertEquals("duplicates in $s", s.size, s.toSet().size)
    }

    // ── Path folding: the fast-swipe path ────────────────────────────────────

    @Test
    fun `a path appends every letter it crossed`() {
        // One pointer event whose segment crossed three letters.
        assertEquals(
            listOf(0, 1, 2, 3),
            WheelSelection.applyPath(listOf(0), listOf(1, 2, 3), pointerIndex = 3),
        )
    }

    @Test
    fun `taking only the last letter of a path would lose letters`() {
        // Guards the subtle version of the skipped-letter bug: detected but
        // never appended.
        val folded = WheelSelection.applyPath(listOf(0), listOf(1, 2, 3), pointerIndex = 3)
        val lastOnly = WheelSelection.apply(listOf(0), 3)
        assertEquals(listOf(0, 1, 2, 3), folded)
        assertEquals(listOf(0, 3), lastOnly)
        assertTrue("folding must retain more than last-only", folded.size > lastOnly.size)
    }

    @Test
    fun `a retrace onto the previous letter pops one`() {
        // Finger comes to rest inside letter 2, the second-to-last.
        assertEquals(
            listOf(0, 1, 2),
            WheelSelection.applyPath(listOf(0, 1, 2, 3), listOf(2), pointerIndex = 2),
        )
    }

    @Test
    fun `a fast backtrack across two letters pops twice`() {
        // Finger flicks from letter 3 back through 2 and lands in 1. Landing
        // on an already-selected letter means this is a retrace, so both
        // crossings undo.
        assertEquals(
            listOf(0, 1),
            WheelSelection.applyPath(listOf(0, 1, 2, 3), listOf(2, 1), pointerIndex = 1),
        )
    }

    @Test
    fun `retracing is decided by where the finger lands, not what it crossed`() {
        val current = listOf(0, 1, 2, 3)

        // Lands on a selected letter -> retrace, crossings undo.
        assertEquals(
            listOf(0, 1),
            WheelSelection.applyPath(current, listOf(2, 1), pointerIndex = 1),
        )

        // Same crossings, but lands on a NEW letter -> drawing forward, so the
        // sweep over 2 and 1 is incidental and must not undo anything.
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            WheelSelection.applyPath(current, listOf(2, 1, 4), pointerIndex = 4),
        )
    }

    /**
     * KNOWN LIMITATION, minor and self-correcting. If a retracing finger is
     * between letters when a sample lands (pointerIndex -1), that sample pops
     * nothing, because there is no way to tell a retrace from a forward sweep
     * without knowing where the finger came to rest. The next sample that
     * lands inside a letter resolves it.
     */
    @Test
    fun `a sample landing in dead space pops nothing`() {
        val current = listOf(0, 1, 2, 3)
        assertEquals(current, WheelSelection.applyPath(current, listOf(2), pointerIndex = -1))
    }

    /**
     * Regression for the RACE bug found on device.
     *
     * Wheel CSEAR, indices C=0 S=1 E=2 A=3 R=4. Swiping R -> A -> C, the hop
     * from A to C spans 144 degrees with R sitting between them, so the finger
     * sweeps back through R. R being the second-to-last selection, the old
     * rule read that as a retrace and popped A: R-A-C-E silently became R-C-E,
     * which reported "not a word".
     */
    @Test
    fun `a letter swept over mid-segment never undoes the selection`() {
        val afterRA = listOf(4, 3)
        // Segment A -> C reports R first (t=0.5), then C (t=1.0). Finger is in C.
        val afterC = WheelSelection.applyPath(afterRA, listOf(4, 0), pointerIndex = 0)
        assertEquals("R must not pop A when merely swept over", listOf(4, 3, 0), afterC)

        val afterE = WheelSelection.applyPath(afterC, listOf(2), pointerIndex = 2)
        assertEquals(listOf(4, 3, 0, 2), afterE)
        assertEquals("RACE", WheelSelection.wordOf(afterE, listOf('C', 'S', 'E', 'A', 'R')))
    }

    @Test
    fun `an empty path changes nothing`() {
        val s = listOf(0, 1)
        assertSame(s, WheelSelection.applyPath(s, emptyList(), pointerIndex = -1))
    }

    @Test
    fun `a path starting from nothing builds a whole word`() {
        assertEquals(
            listOf(4, 2, 0),
            WheelSelection.applyPath(emptyList(), listOf(4, 2, 0), pointerIndex = 0),
        )
    }

    @Test
    fun `a path repeating the current letter is a no-op`() {
        val s = listOf(0, 1)
        assertEquals(s, WheelSelection.applyPath(s, listOf(1, 1, 1), pointerIndex = 1))
    }

    // ── Word assembly ────────────────────────────────────────────────────────

    @Test
    fun `word reflects selection order, not letter order`() {
        assertEquals("RATE", WheelSelection.wordOf(listOf(3, 2, 1, 4), letters))
        assertEquals("STAR", WheelSelection.wordOf(listOf(0, 1, 2, 3), letters))
    }

    @Test
    fun `empty selection spells nothing`() {
        assertEquals("", WheelSelection.wordOf(emptyList(), letters))
    }

    @Test
    fun `out of range indices are dropped rather than crashing`() {
        // Should be unreachable, but a crash mid-swipe is far worse than a
        // short word.
        assertEquals("SR", WheelSelection.wordOf(listOf(0, 99, 3), letters))
    }

    // ── Submission threshold ─────────────────────────────────────────────────

    @Test
    fun `selections shorter than three letters are not submittable`() {
        assertFalse(WheelSelection.isSubmittable(emptyList()))
        assertFalse(WheelSelection.isSubmittable(listOf(0)))
        assertFalse(WheelSelection.isSubmittable(listOf(0, 1)))
        assertTrue(WheelSelection.isSubmittable(listOf(0, 1, 2)))
    }

    @Test
    fun `short selections are still built normally during the drag`() {
        // The threshold gates submission only. A two-letter selection must
        // still exist and still draw; blocking it feels broken.
        val s = WheelSelection.applyPath(emptyList(), listOf(0, 1), pointerIndex = 1)
        assertEquals(listOf(0, 1), s)
        assertEquals("ST", WheelSelection.wordOf(s, letters))
    }
}
