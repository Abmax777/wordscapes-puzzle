package com.wordscapes.puzzle.ui.game.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.wordscapes.puzzle.ui.theme.GameColors
import kotlinx.coroutines.launch

/** Android's minimum recommended touch target is 48 dp across. */
private val MIN_TOUCH_TARGET_RADIUS = 24.dp

/** How much larger a letter jumps at the instant it is captured. */
private const val CAPTURE_POP = 0.20f

/**
 * The letter wheel: drawing plus continuous swipe selection. Caller owns the size.
 * Raw `awaitEachGesture`; `detectDragGestures` would eat the first ~16 dp of a swipe.
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

    // 1f at capture, springing back to 0f: instant jump, smooth recovery.
    val capture = remember(letters.size) {
        List(letters.size) { Animatable(0f) }
    }

    // snapshotFlow, not LaunchedEffect(state.selection): reacting to selection
    // without recomposing. An effect key here would recompose ~120 times a second.
    LaunchedEffect(capture) {
        var previous = emptyList<Int>()
        snapshotFlow { state.selection }.collect { current ->
            val currentSet = current.toSet()
            current.filterNot { it in previous }.forEach { i ->
                capture.getOrNull(i)?.let { anim ->
                    launch {
                        anim.snapTo(1f)
                        anim.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                }
            }
            // Retraced letters drop their pop rather than animating out.
            previous.filterNot { it in currentSet }.forEach { i ->
                capture.getOrNull(i)?.let { anim -> launch { anim.snapTo(0f) } }
            }
            previous = current
        }
    }

    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minHitRadiusPx = with(density) { MIN_TOUCH_TARGET_RADIUS.toPx() }

        // Needed at composition time: the gesture handler hit-tests against it.
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
                        // Take the press even if an ancestor has seen it.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        down.consume()

                        // Resting radius: a stationary press is deliberate and
                        // can be precise, unlike a drag.
                        state.beginDrag(
                            down.position,
                            geometry.hitTestResting(down.position),
                        )
                        var previous = down.position
                        var completed = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()

                                // Follow only the pointer that started this gesture;
                                // a second finger is ignored entirely.
                                val change = event.changes.firstOrNull { it.id == pointerId }

                                if (change == null || !change.pressed) {
                                    submit(state, currentLetters, currentOnSubmit)
                                    completed = true
                                    break
                                }

                                if (change.position != previous) {
                                    // Segment, not point: at speed the gap between
                                    // samples can exceed a letter.
                                    val crossed =
                                        geometry.hitTestSegment(previous, change.position)
                                    state.onMove(
                                        position = change.position,
                                        crossed = crossed,
                                        // Only drives retrace, so use the strict radius.
                                        pointerIndex =
                                            geometry.hitTestResting(change.position),
                                    )
                                    previous = change.position
                                }
                                change.consume()
                            }
                        } finally {
                            // Rotation, size change, parent steal: drop without submitting.
                            if (!completed) state.cancel()
                        }
                    }
                }
                .drawWithCache {
                    // Cache phase: re-runs only on geometry or glyph change.
                    val lineWidth = geometry.letterRadius * 0.34f
                    val selectedRing = Stroke(width = geometry.letterRadius * 0.16f)

                    onDrawBehind {
                        // Draw phase: state read here invalidates draw only.
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

                        // Single Path so joins are mitred rather than seamed.
                        if (selection.isNotEmpty()) {
                            val path = Path()
                            val first = geometry.letterCenters[selection.first()]
                            path.moveTo(first.x, first.y)
                            for (i in 1 until selection.size) {
                                val c = geometry.letterCenters[selection[i]]
                                path.lineTo(c.x, c.y)
                            }
                            if (pointer != null) {
                                // No smoothing: easing here reads as input lag.
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
                            // Read at draw time: a frame costs a redraw, not a recompose.
                            val pop = 1f + (capture.getOrNull(i)?.value ?: 0f) * CAPTURE_POP

                            scale(scale = pop, pivot = c) {
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

/** Clears the selection before emitting, so a second swipe cannot append to it. */
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
