package com.wordscapes.puzzle.ui.game.wheel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Transient gesture state for the wheel: what is selected right now, and where
 * the finger is.
 *
 * ## Why this is a class and not composable parameters
 *
 * Selection changes on every pointer move — up to 120 times a second. If it
 * were a `List<Int>` parameter on [LetterWheel], every change would recompose
 * the composable, rebuild its modifier chain, and invalidate the
 * `drawWithCache` block, recomputing all the trig and re-measuring every glyph
 * for a frame in which none of that changed.
 *
 * Holding it in a `@Stable` object instead means the *identity* passed to
 * [LetterWheel] never changes, so no recomposition happens at all. The
 * `mutableStateOf` fields are read inside `onDrawBehind`, which invalidates
 * only the draw phase. That is the deferred-read pattern, and it is the whole
 * reason the wheel can track a finger without dropping frames.
 *
 * The rule to carry forward: read rapidly-changing state as late as possible.
 * Reading it in the composable body costs a recomposition; reading it in a
 * cache block costs a cache rebuild; reading it in a draw lambda costs only a
 * redraw.
 *
 * ## Why none of this is in the ViewModel
 *
 * Gesture state is worthless across process death — nobody wants a half-drawn
 * swipe restored after their phone is killed in the background. Level progress
 * is not. Keeping them apart is what keeps `SavedStateHandle` small and means
 * no gesture state ever needs serialising.
 */
@Stable
class WheelGestureState {

    /** Indices into the wheel's letter list, in the order they were selected. */
    var selection by mutableStateOf<List<Int>>(emptyList())
        private set

    /**
     * Raw pointer position, or null when no drag is in progress.
     *
     * Deliberately raw — no smoothing, no interpolation. Any easing applied
     * here shows up as the live line lagging behind the fingertip, which reads
     * as input lag even at a few milliseconds.
     */
    var pointer by mutableStateOf<Offset?>(null)
        private set

    var isDragging by mutableStateOf(false)
        private set

    /** Pointer samples in the current drag. Debug overlay only. */
    var sampleCount by mutableIntStateOf(0)
        private set

    /** Last word emitted, for the debug readout. */
    var lastSubmitted by mutableStateOf("")
        private set

    internal fun beginDrag(position: Offset, hitIndex: Int) {
        isDragging = true
        pointer = position
        sampleCount = 1
        selection = if (hitIndex >= 0) listOf(hitIndex) else emptyList()
    }

    /**
     * @param pointerIndex the letter the finger is inside right now, or -1.
     *   Only this letter may trigger a backtrack; letters merely swept over
     *   mid-segment can append but never undo. See WheelSelection.applyPath.
     */
    internal fun onMove(position: Offset, crossed: List<Int>, pointerIndex: Int) {
        pointer = position
        sampleCount++
        if (crossed.isNotEmpty()) {
            val next = WheelSelection.applyPath(selection, crossed, pointerIndex)
            // Identity check: applyPath returns the same instance when nothing
            // changed, so a finger resting inside a letter costs no snapshot
            // write and therefore no redraw of the selection.
            if (next !== selection) selection = next
        }
    }

    /**
     * End the drag and hand back what was selected.
     *
     * Clears local state *before* returning, so the caller invokes validation
     * on an already-empty wheel. This is what makes rapid consecutive swipes
     * safe: the next gesture can start on a clean selection while the previous
     * word is still being validated and animated. If clearing waited for
     * validation to resolve, a fast second swipe would append onto the first
     * word's selection.
     */
    internal fun endDrag(): List<Int> {
        val finished = selection
        selection = emptyList()
        pointer = null
        isDragging = false
        return finished
    }

    /** Abandon the drag without submitting — cancellation, size change, rotation. */
    internal fun cancel() {
        selection = emptyList()
        pointer = null
        isDragging = false
    }

    internal fun recordSubmission(word: String) {
        lastSubmitted = word
    }
}
