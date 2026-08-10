package com.wordscapes.puzzle.ui.game.wheel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.wordscapes.puzzle.ui.theme.GameColors

/** Android's minimum recommended touch target is 48 dp across. */
private val MIN_TOUCH_TARGET_RADIUS = 24.dp

/**
 * The letter wheel: drawing plus continuous swipe selection.
 *
 * ## Sizing
 *
 * Does not impose a size — the caller decides via [modifier]. The disc is sized
 * from the smaller dimension so it stays circular at any aspect ratio.
 *
 * ## Why `awaitEachGesture` and not `detectDragGestures`
 *
 * `detectDragGestures` waits for touch slop (~16 dp of movement) before firing
 * `onDragStart`. For a wheel that is wrong twice over: the letter under the
 * initial press would not be selected until the finger had already travelled
 * past it, and the first ~16 dp of every swipe would be silently discarded.
 * The raw `awaitEachGesture` loop lets the press itself select a letter, which
 * is what makes the control feel immediate.
 *
 * ## Why `pointerInput` is keyed on `geometry`
 *
 * `pointerInput(key)` cancels and restarts its coroutine when the key changes.
 * Keying on [WheelGeometry] means a size change tears down the in-flight
 * gesture and rebuilds against correct positions — stale geometry would
 * hit-test against letter centres that have moved. It also gives the right
 * behaviour for rotation mid-drag: the gesture is cancelled rather than
 * completed against a stale layout.
 *
 * The keys are a dependency list, exactly like `remember(key)`. Passing `Unit`
 * — a common mistake — captures the first frame's values forever, so the wheel
 * would keep hit-testing against the original size after any resize.
 *
 * ## Why callbacks go through `rememberUpdatedState`
 *
 * The gesture coroutine outlives many recompositions. A directly captured
 * `onWordSubmitted` would be frozen at whatever it was when the coroutine
 * started, so a caller that swaps in a new lambda would find submissions still
 * going to the old one. `rememberUpdatedState` keeps the capture pointing at
 * the current value without restarting the gesture.
 */
@Composable
fun LetterWheel(
    letters: List<Char>,
    modifier: Modifier = Modifier,
    state: WheelGestureState = remember { WheelGestureState() },
    onWordSubmitted: (String) -> Unit = {},
    showHitTargets: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Captured by the long-lived gesture coroutine; must not go stale.
    val currentLetters by rememberUpdatedState(letters)
    val currentOnSubmit by rememberUpdatedState(onWordSubmitted)

    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minHitRadiusPx = with(density) { MIN_TOUCH_TARGET_RADIUS.toPx() }

        // Geometry is needed at composition time, not just at draw time,
        // because the gesture handler hit-tests against it. Recomputed only
        // when the inputs actually change.
        val geometry = remember(widthPx, heightPx, letters.size, minHitRadiusPx) {
            WheelGeometry.compute(widthPx, heightPx, letters.size, minHitRadiusPx)
        }

        val glyphs = remember(geometry, letters) {
            val style = TextStyle(
                color = GameColors.LetterText,
                fontSize = with(density) { (geometry.letterRadius * 1.05f).toSp() },
                fontWeight = FontWeight.Bold,
            )
            letters.map { textMeasurer.measure(AnnotatedString(it.uppercase()), style) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(geometry) {
                    awaitEachGesture {
                        // requireUnconsumed = false: take the press even if an
                        // ancestor has already looked at it.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        down.consume()

                        // Resting radius on the initial press, not the
                        // generous reach radius.
                        //
                        // A press is stationary and deliberate — the player is
                        // aiming, and can be precise. A drag is neither, which
                        // is what the generous radius exists for. Using the
                        // reach radius here meant every point on the disc
                        // resolved to some letter, since inflated hit circles
                        // leave no gaps: you could not put a finger down
                        // without committing to a letter.
                        //
                        // Costs nothing when the player is slightly off: the
                        // first segment test of the drag sweeps the intended
                        // letter with the generous radius and appends it.
                        state.beginDrag(
                            down.position,
                            geometry.hitTestResting(down.position),
                        )
                        var previous = down.position
                        var completed = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()

                                // Follow only the pointer that started this
                                // gesture. A second finger landing mid-drag is
                                // ignored entirely rather than hijacking the
                                // selection or cancelling it.
                                val change = event.changes.firstOrNull { it.id == pointerId }

                                if (change == null || !change.pressed) {
                                    submit(state, currentLetters, currentOnSubmit)
                                    completed = true
                                    break
                                }

                                if (change.position != previous) {
                                    // Segment test, not point test: at speed
                                    // the gap between samples can be wider
                                    // than a letter.
                                    val crossed =
                                        geometry.hitTestSegment(previous, change.position)
                                    state.onMove(
                                        position = change.position,
                                        crossed = crossed,
                                        // Resting test, not the generous hit
                                        // test: this index only ever drives
                                        // retrace, and undoing must be
                                        // deliberate.
                                        pointerIndex =
                                            geometry.hitTestResting(change.position),
                                    )
                                    previous = change.position
                                }
                                change.consume()
                            }
                        } finally {
                            // Cancellation — rotation, size change, a parent
                            // stealing the gesture. Drop the selection without
                            // submitting a word the player never finished.
                            if (!completed) state.cancel()
                        }
                    }
                }
                .drawWithCache {
                    // ── Cache phase: only re-runs when geometry or glyphs change ──
                    val lineWidth = geometry.letterRadius * 0.34f
                    val selectedRing = Stroke(width = geometry.letterRadius * 0.16f)

                    onDrawBehind {
                        // ── Draw phase: state read here invalidates only the
                        //    draw, never the cache above ──
                        val selection = state.selection
                        val pointer = state.pointer

                        drawCircle(
                            color = GameColors.WheelDisc,
                            radius = geometry.discRadius,
                            center = geometry.center,
                        )

                        if (showHitTargets) {
                            geometry.letterCenters.forEach { c ->
                                drawCircle(DEBUG_HIT_FILL, geometry.hitRadius, c)
                                drawCircle(
                                    DEBUG_HIT_STROKE, geometry.hitRadius, c,
                                    style = Stroke(width = 2f),
                                )
                            }
                        }

                        // Trail: joined segments through every selected centre,
                        // then one live segment to the raw pointer. Drawn as a
                        // single Path so the joins are mitred rather than
                        // showing seams at each letter.
                        if (selection.isNotEmpty()) {
                            val path = Path()
                            val first = geometry.letterCenters[selection.first()]
                            path.moveTo(first.x, first.y)
                            for (i in 1 until selection.size) {
                                val c = geometry.letterCenters[selection[i]]
                                path.lineTo(c.x, c.y)
                            }
                            if (pointer != null) {
                                // No smoothing on this segment — easing here
                                // reads as input lag.
                                path.lineTo(pointer.x, pointer.y)
                            }
                            drawPath(
                                path = path,
                                color = GameColors.SelectionLine,
                                style = Stroke(
                                    width = lineWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }

                        geometry.letterCenters.forEachIndexed { i, c ->
                            val isSelected = i in selection
                            drawCircle(
                                color = if (isSelected) GameColors.LetterSelected
                                else GameColors.LetterTile,
                                radius = geometry.letterRadius,
                                center = c,
                            )
                            if (isSelected) {
                                drawCircle(
                                    color = Color.White,
                                    radius = geometry.letterRadius,
                                    center = c,
                                    style = selectedRing,
                                )
                            }
                            val g = glyphs[i]
                            drawText(
                                textLayoutResult = g,
                                topLeft = Offset(
                                    x = c.x - g.size.width / 2f,
                                    y = c.y - g.size.height / 2f,
                                ),
                            )
                        }

                        if (showHitTargets) {
                            drawCircle(
                                color = DEBUG_ORBIT,
                                radius = geometry.orbitRadius,
                                center = geometry.center,
                                style = Stroke(width = 1.5f),
                            )
                            pointer?.let {
                                drawCircle(DEBUG_POINTER, 8f, it)
                            }
                        }
                    }
                },
        )
    }
}

/**
 * Clear first, then emit.
 *
 * The ordering is the point. [WheelGestureState.endDrag] wipes the selection
 * before this function calls back, so validation and its animations run
 * against an already-empty wheel. A second swipe starting immediately cannot
 * append onto the previous word.
 */
private fun submit(
    state: WheelGestureState,
    letters: List<Char>,
    onWordSubmitted: (String) -> Unit,
) {
    val finished = state.endDrag()
    if (!WheelSelection.isSubmittable(finished)) return
    val word = WheelSelection.wordOf(finished, letters)
    state.recordSubmission(word)
    onWordSubmitted(word)
}

private val DEBUG_HIT_FILL = Color(0x2200E5FF)
private val DEBUG_HIT_STROKE = Color(0x9900E5FF)
private val DEBUG_ORBIT = Color(0x66FF4081)
private val DEBUG_POINTER = Color(0xFFFF4081)
