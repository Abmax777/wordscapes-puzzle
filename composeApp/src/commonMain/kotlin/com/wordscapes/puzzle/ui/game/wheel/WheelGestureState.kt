package com.wordscapes.puzzle.ui.game.wheel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Transient gesture state in a @Stable holder, so pointer moves never recompose
 * [LetterWheel]. Not in the ViewModel: a half-drawn swipe need not survive death.
 */
@Stable
class WheelGestureState {

    /** Indices into the wheel's letter list, in the order they were selected. */
    var selection by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Raw pointer position. No smoothing — easing here reads as input lag. */
    var pointer by mutableStateOf<Offset?>(null)
        private set

    var isDragging by mutableStateOf(false)
        private set

    /** Pointer samples in the current drag. Diagnostic only. */
    var sampleCount by mutableIntStateOf(0)
        private set

    /** Last word emitted. Diagnostic only. */
    var lastSubmitted by mutableStateOf("")
        private set

    internal fun beginDrag(position: Offset, hitIndex: Int) {
        isDragging = true
        pointer = position
        sampleCount = 1
        selection = if (hitIndex >= 0) listOf(hitIndex) else emptyList()
    }

    /** @param pointerIndex letter the finger is inside now, or -1. Drives retrace. */
    internal fun onMove(position: Offset, crossed: List<Int>, pointerIndex: Int) {
        pointer = position
        sampleCount++

        // Unconditional: retrace depends on where the finger rests, not on the path.
        val next = WheelSelection.applyPath(selection, crossed, pointerIndex)

        // applyPath returns the same instance on no-op, so this avoids a redraw.
        if (next !== selection) selection = next
    }

    /**
     * Clears state *before* returning, so validation runs on an empty wheel and
     * a fast second swipe cannot append onto the previous word.
     */
    internal fun endDrag(): List<Int> {
        val finished = selection
        selection = emptyList()
        pointer = null
        isDragging = false
        return finished
    }

    /** Abandon without submitting: cancellation, size change, rotation. */
    internal fun cancel() {
        selection = emptyList()
        pointer = null
        isDragging = false
    }

    internal fun recordSubmission(word: String) {
        lastSubmitted = word
    }
}
