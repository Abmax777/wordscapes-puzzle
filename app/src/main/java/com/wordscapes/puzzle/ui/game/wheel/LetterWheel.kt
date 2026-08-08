package com.wordscapes.puzzle.ui.game.wheel

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * The letter wheel, drawing only. No gesture handling yet — that arrives in
 * step 3, and keeping it out until the drawing is right means a misbehaving
 * swipe can only be the gesture code, never the layout.
 *
 * ## Why `drawWithCache` rather than `Canvas`
 *
 * `Modifier.drawWithCache` splits work into two phases. The outer block runs
 * only when the layout size changes (or when a State read *inside it* changes)
 * and is where expensive setup belongs — here, the trig for
 * [WheelGeometry] and measuring each glyph. The `onDrawBehind` lambda runs on
 * every frame that needs redrawing and does nothing but issue draw commands.
 *
 * With a plain `Canvas`, the whole lambda is the draw lambda, so geometry and
 * text measurement would be recomputed on every single frame. During a drag
 * that is 120 recomputations a second producing identical results — the exact
 * kind of waste that shows up as dropped frames on a mid-range device, which
 * reads as "prototype" faster than a missing animation does.
 *
 * This split is also what satisfies the plan's "positions computed once per
 * size change, cached" requirement without any manual caching or `remember`
 * keyed on size.
 *
 * ## Sizing
 *
 * This composable does **not** impose a size. The caller decides, via
 * [modifier], and the wheel fills whatever it is given, sizing the disc from
 * the smaller dimension so it stays circular in any aspect ratio.
 *
 * That is the standard Compose convention, and skipping it caused a real bug:
 * an earlier version called `fillMaxSize()` internally while the sandbox
 * asked for `fillMaxWidth().aspectRatio(1f)`. On a wide landscape screen that
 * produced a square as tall as the screen was wide — far taller than the
 * viewport — and because Compose does not clip children to their bounds by
 * default, the wheel drew straight over the header and controls.
 */
@Composable
fun LetterWheel(
    letters: List<Char>,
    modifier: Modifier = Modifier,
    showHitTargets: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    val minHitRadiusPx = with(LocalDensity.current) { MIN_TOUCH_TARGET_RADIUS.toPx() }

    Box(
        modifier = modifier
            .drawWithCache {
                // ── Cache phase: size changed, recompute everything static ──
                val geometry = WheelGeometry.compute(
                    widthPx = size.width,
                    heightPx = size.height,
                    letterCount = letters.size,
                    minHitRadiusPx = minHitRadiusPx,
                )

                val glyphStyle = TextStyle(
                    color = GameColors.LetterText,
                    fontSize = (geometry.letterRadius * 1.05f).toSp(),
                    fontWeight = FontWeight.Bold,
                )
                // Measured once per size change, not per frame.
                val glyphs = letters.map { ch ->
                    textMeasurer.measure(AnnotatedString(ch.uppercase()), glyphStyle)
                }

                // ── Draw phase: runs per frame, issues commands only ────────
                onDrawBehind {
                    drawCircle(
                        color = GameColors.WheelDisc,
                        radius = geometry.discRadius,
                        center = geometry.center,
                    )

                    if (showHitTargets) {
                        geometry.letterCenters.forEach { c ->
                            drawCircle(
                                color = DEBUG_HIT_FILL,
                                radius = geometry.hitRadius,
                                center = c,
                            )
                            drawCircle(
                                color = DEBUG_HIT_STROKE,
                                radius = geometry.hitRadius,
                                center = c,
                                style = Stroke(width = 2f),
                            )
                        }
                    }

                    geometry.letterCenters.forEachIndexed { i, c ->
                        drawCircle(
                            color = GameColors.LetterTile,
                            radius = geometry.letterRadius,
                            center = c,
                        )
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
                        // Orbit ring and centre cross, to sanity-check that
                        // letters really are evenly spaced on a true circle.
                        drawCircle(
                            color = DEBUG_ORBIT,
                            radius = geometry.orbitRadius,
                            center = geometry.center,
                            style = Stroke(width = 1.5f),
                        )
                        val arm = 12f
                        drawLine(
                            DEBUG_ORBIT,
                            Offset(geometry.center.x - arm, geometry.center.y),
                            Offset(geometry.center.x + arm, geometry.center.y),
                        )
                        drawLine(
                            DEBUG_ORBIT,
                            Offset(geometry.center.x, geometry.center.y - arm),
                            Offset(geometry.center.x, geometry.center.y + arm),
                        )
                    }
                }
            },
    )
}

private val DEBUG_HIT_FILL = Color(0x2200E5FF)
private val DEBUG_HIT_STROKE = Color(0x9900E5FF)
private val DEBUG_ORBIT = Color(0x66FF4081)
