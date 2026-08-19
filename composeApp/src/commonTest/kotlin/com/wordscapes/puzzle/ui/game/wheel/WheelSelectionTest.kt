package com.wordscapes.puzzle.ui.game.wheel

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Covers the selection rules, which are otherwise only verifiable by dragging
 * a thumb and watching. Several cases correspond directly to bugs found on
 * device — each is marked, because those are the ones a refactor must not
 * quietly undo.
 */
class WheelSelectionTest {

    private val letters = listOf('S', 'T', 'A', 'R', 'E')

    /** Convenience: one sample landing inside [pointer], sweeping [path]. */
    private fun step(current: List<Int>, vararg path: Int, pointer: Int) =
        WheelSelection.applyPath(current, path.toList(), pointer)

    // ── Appending ────────────────────────────────────────────────────────────

    @Test
    fun `first letter starts the selection`() {
        assertEquals(listOf(2), step(emptyList(), 2, pointer = 2))
    }

    @Test
    fun `letters append in travel order`() {
        var s = emptyList<Int>()
        listOf(3, 0, 4).forEach { s = step(s, it, pointer = it) }
        assertEquals(listOf(3, 0, 4), s)
    }

    @Test
    fun `one sample crossing three letters appends all of them`() {
        // Fast flick: the segment swept 1, 2 and 3 and landed in 3.
        assertEquals(listOf(0, 1, 2, 3), step(listOf(0), 1, 2, 3, pointer = 3))
    }

    @Test
    fun `taking only the last letter of a path would lose letters`() {
        val folded = step(listOf(0), 1, 2, 3, pointer = 3)
        val lastOnly = step(listOf(0), 3, pointer = 3)
        assertTrue(folded.size > lastOnly.size, "folding must retain more than last-only")
    }

    @Test
    fun `append ignores a letter already in the word`() {
        val s = listOf(0, 1, 2)
        assertSame(s, WheelSelection.append(s, 1), "no-op must not copy")
    }

    @Test
    fun `holding still inside a letter does not repeat it`() {
        // A resting finger emits a stream of identical move events.
        var s = listOf(0)
        repeat(50) { s = step(s, 0, pointer = 0) }
        assertEquals(listOf(0), s, "holding still must not spell SSSS...")
    }

    // ── Retracing ────────────────────────────────────────────────────────────

    @Test
    fun `resting on the previous letter truncates by one`() {
        assertEquals(listOf(0, 1, 2), step(listOf(0, 1, 2, 3), 2, pointer = 2))
    }

    @Test
    fun `a fast flick back across two letters truncates by two`() {
        assertEquals(listOf(0, 1), step(listOf(0, 1, 2, 3), 2, 1, pointer = 1))
    }

    @Test
    fun `retrace distance does not matter`() {
        // Landing on the first letter unwinds the whole word regardless of
        // how many samples it took to get there.
        assertEquals(listOf(0), step(listOf(0, 1, 2, 3), 2, 1, 0, pointer = 0))
    }

    @Test
    fun `retracing then drawing forward re-appends`() {
        var s = step(listOf(0, 1, 2), 1, pointer = 1)      // -> [0,1]
        assertEquals(listOf(0, 1), s)
        s = step(s, 2, pointer = 2)                        // -> [0,1,2]
        assertEquals(listOf(0, 1, 2), s)
    }

    @Test
    fun `retracing never empties the selection`() {
        // Finger back on the only selected letter: it is the last, so this is
        // forward-drawing, not a retrace.
        assertEquals(listOf(0), step(listOf(0), 0, pointer = 0))
    }

    // ── Bugs found on device ─────────────────────────────────────────────────

    /**
     * REGRESSION: the second letter kept dropping on fast swipes.
     *
     * With [S, I] selected and the thumb still inside I, adjacent hit circles
     * overlap enough that the segment as the thumb starts leaving I grazes S.
     * An earlier rule treated "finger inside any selected letter" as a
     * retrace, so S — the second-to-last — popped I off.
     *
     * Resting inside the LAST letter is ordinary forward drawing.
     */
    @Test
    fun `grazing the previous letter while still on the current one keeps both`() {
        assertEquals(listOf(0, 1), step(listOf(0, 1), 0, 1, pointer = 1))
        assertEquals(listOf(0, 1), step(listOf(0, 1), 0, pointer = 1))
    }

    /**
     * REGRESSION: R-A-C-E came out as R-C-E.
     *
     * Wheel CSEAR, indices C=0 S=1 E=2 A=3 R=4. The hop from A to C spans 144
     * degrees with R between them, so the arc a finger draws sweeps back
     * through R. R being the second-to-last selection, an earlier rule read
     * that as a retrace and popped A.
     */
    @Test
    fun `a letter swept over on the way somewhere new never undoes anything`() {
        val afterRA = listOf(4, 3)
        val afterC = step(afterRA, 4, 0, pointer = 0)   // sweeps R, lands in C
        assertEquals(listOf(4, 3, 0), afterC, "R must not pop A")

        val afterE = step(afterC, 2, pointer = 2)
        assertEquals(listOf(4, 3, 0, 2), afterE)
        assertEquals(WheelSelection.wordOf(afterE, listOf('C', 'S', 'E', 'A', 'R')), "RACE")
    }

    @Test
    fun `identical crossings resolve differently depending on where the finger lands`() {
        val current = listOf(0, 1, 2, 3)
        // Lands on a selected letter -> retrace.
        assertEquals(listOf(0, 1), step(current, 2, 1, pointer = 1))
        // Same crossings, lands on a new letter -> forward, nothing undone.
        assertEquals(listOf(0, 1, 2, 3, 4), step(current, 2, 1, 4, pointer = 4))
    }

    /**
     * KNOWN LIMITATION, minor and self-correcting. A sample landing between
     * letters cannot be classified — there is no resting letter to read intent
     * from — so it appends only. The next sample inside a letter resolves it.
     */
    @Test
    fun `a sample landing in dead space appends but never retraces`() {
        assertEquals(listOf(0, 1, 2, 3), step(listOf(0, 1, 2, 3), 2, pointer = -1))
        assertEquals(listOf(0, 1, 4), step(listOf(0, 1), 4, pointer = -1))
    }

    @Test
    fun `a letter is never used twice in one word`() {
        var s = emptyList<Int>()
        listOf(0, 1, 2, 0, 1, 3).forEach { s = step(s, it, pointer = it) }
        assertEquals(s.size, s.toSet().size, "duplicates in $s")
    }

    // ── Word assembly ────────────────────────────────────────────────────────

    @Test
    fun `word reflects selection order, not letter order`() {
        assertEquals(WheelSelection.wordOf(listOf(3, 2, 1, 4), letters), "RATE")
        assertEquals(WheelSelection.wordOf(listOf(0, 1, 2, 3), letters), "STAR")
    }

    @Test
    fun `empty selection spells nothing`() {
        assertEquals(WheelSelection.wordOf(emptyList(), letters), "")
    }

    @Test
    fun `out of range indices are dropped rather than crashing`() {
        assertEquals(WheelSelection.wordOf(listOf(0, 99, 3), letters), "SR")
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
        val s = step(emptyList(), 0, 1, pointer = 1)
        assertEquals(listOf(0, 1), s)
        assertEquals(WheelSelection.wordOf(s, letters), "ST")
    }
}
