package com.wordscapes.puzzle.ui.game.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.wordscapes.puzzle.domain.model.GridPosition
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.ui.theme.GameColors
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.sin

/** How long a single tile takes to pop in. */
private const val TILE_REVEAL_MS = 240

/** Offset between consecutive tiles of the same word, so it reads left-to-right. */
private const val TILE_STAGGER_MS = 45

/** A single tile's pulse when the board is finished. */
private const val COMPLETE_PULSE_MS = 260

/** How long the completion wave takes to travel from centre to edge. */
private const val COMPLETE_WAVE_MS = 340

/**
 * The crossword grid.
 *
 * Cells come from [Level.cells], derived and validated by the data layer at
 * parse time, so this composable never reasons about whether a placement is
 * legal — only about drawing it.
 *
 * A cell shows its letter once *any* word through it has been revealed. That
 * is why [com.wordscapes.puzzle.domain.model.GridCell] carries a set of word
 * indices rather than one: revealing STAIR must also fill the shared letters of
 * every word crossing it.
 *
 * ## Reveal animation
 *
 * One [Animatable] per revealed word, held in a snapshot map — not one shared
 * animation for the grid. Two words revealed in quick succession then animate
 * independently instead of the second cancelling the first, which is the whole
 * point of keying feedback per word rather than per screen.
 *
 * Each tile's own progress is derived from its position in the word at draw
 * time, so the stagger costs no extra animations: the word's Animatable runs
 * for the full staggered duration and each tile reads its slice out of it.
 *
 * Words already revealed when the screen appears are seeded at 1f and never
 * animate. Without that, returning from process death or a rotation would
 * replay every reveal at once, which reads as a bug rather than as polish.
 */
@Composable
fun CrosswordGrid(
    level: Level,
    revealedWordIndices: Set<Int>,
    isComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Word index -> reveal progress. Keyed on level.id so advancing to the
    // next level starts clean rather than inheriting the previous board.
    val reveals = remember(level.id) { mutableStateMapOf<Int, Animatable<Float, *>>() }

    // Captured on first composition for this level: anything in here was
    // already revealed on entry and must appear instantly.
    val revealedOnEntry = remember(level.id) { revealedWordIndices }

    LaunchedEffect(level.id, revealedWordIndices) {
        revealedWordIndices.forEach { wordIndex ->
            if (reveals.containsKey(wordIndex)) return@forEach

            if (wordIndex in revealedOnEntry) {
                reveals[wordIndex] = Animatable(1f)
            } else {
                val anim = Animatable(0f)
                reveals[wordIndex] = anim
                val letters = level.words[wordIndex].word.length
                val duration = TILE_REVEAL_MS + TILE_STAGGER_MS * (letters - 1)
                // Its own coroutine, so a second word revealed mid-animation
                // runs alongside rather than waiting.
                launch {
                    anim.animateTo(1f, tween(duration, easing = LinearEasing))
                }
            }
        }
    }

    // Completion wave. One animation for the whole board, with each tile's
    // delay derived from its distance to the centre — so it radiates outward
    // rather than every tile pulsing in unison.
    val completion = remember(level.id) { Animatable(0f) }
    LaunchedEffect(level.id, isComplete) {
        if (isComplete && completion.value == 0f) {
            completion.animateTo(
                targetValue = 1f,
                animationSpec = tween(COMPLETE_PULSE_MS + COMPLETE_WAVE_MS, easing = LinearEasing),
            )
        }
    }

    Box(
        modifier = modifier.drawWithCache {
            val cell = minOf(
                size.width / level.gridWidth,
                size.height / level.gridHeight,
            )
            val gridWidthPx = cell * level.gridWidth
            val gridHeightPx = cell * level.gridHeight
            val originX = (size.width - gridWidthPx) / 2f
            val originY = (size.height - gridHeightPx) / 2f

            val inset = cell * 0.06f
            val boxSize = Size(cell - inset * 2, cell - inset * 2)
            val corner = CornerRadius(cell * 0.16f)

            val style = TextStyle(
                color = GameColors.LetterText,
                fontSize = with(density) { (cell * 0.52f).toSp() },
                fontWeight = FontWeight.Bold,
            )
            // Measured once per size change, not once per reveal.
            val glyphs = level.cells.mapValues { (_, c) ->
                textMeasurer.measure(AnnotatedString(c.letter.toString()), style)
            }

            // Which position each cell occupies within each word, so a tile can
            // work out its stagger slice without searching at draw time.
            val offsets: Map<GridPosition, Map<Int, Int>> = level.cells.keys.associateWith { pos ->
                buildMap {
                    level.words.forEachIndexed { wordIndex, placed ->
                        val i = placed.positions.indexOf(pos)
                        if (i >= 0) put(wordIndex, i)
                    }
                }
            }

            // Normalised 0..1 distance from the grid centre, precomputed so the
            // wave costs nothing per frame.
            val centreRow = (level.gridHeight - 1) / 2f
            val centreCol = (level.gridWidth - 1) / 2f
            val maxDistance = level.cells.keys.maxOfOrNull {
                hypot(it.row - centreRow, it.col - centreCol)
            }?.takeIf { it > 0f } ?: 1f
            val waveDelay: Map<GridPosition, Float> = level.cells.keys.associateWith {
                hypot(it.row - centreRow, it.col - centreCol) / maxDistance
            }

            fun topLeftOf(pos: GridPosition) = Offset(
                x = originX + pos.col * cell + inset,
                y = originY + pos.row * cell + inset,
            )

            onDrawBehind {
                level.cells.forEach { (pos, gridCell) ->
                    // Snapshot reads happen here, in the draw lambda, so an
                    // animation frame invalidates drawing only — never the
                    // cache above, and never a recomposition.
                    val progress = gridCell.wordIndices.maxOfOrNull { wordIndex ->
                        val anim = reveals[wordIndex] ?: return@maxOfOrNull 0f
                        val letters = level.words[wordIndex].word.length
                        val duration = TILE_REVEAL_MS + TILE_STAGGER_MS * (letters - 1)
                        val elapsed = anim.value * duration
                        val delay = (offsets[pos]?.get(wordIndex) ?: 0) * TILE_STAGGER_MS
                        ((elapsed - delay) / TILE_REVEAL_MS).coerceIn(0f, 1f)
                    } ?: 0f

                    val tl = topLeftOf(pos)

                    // Empty slot, always drawn, so the grid's shape is visible
                    // before anything is solved.
                    drawRoundRect(
                        color = GameColors.UnrevealedCell.copy(alpha = 0.22f),
                        topLeft = tl,
                        size = boxSize,
                        cornerRadius = corner,
                    )

                    if (progress <= 0f) return@forEach

                    // Completion pulse: 0 -> 1 -> 0, offset by distance from
                    // centre so the board finishes with a ripple.
                    val pulse = if (completion.value <= 0f) 0f else {
                        val elapsed = completion.value * (COMPLETE_PULSE_MS + COMPLETE_WAVE_MS)
                        val delay = (waveDelay[pos] ?: 0f) * COMPLETE_WAVE_MS
                        val local = ((elapsed - delay) / COMPLETE_PULSE_MS).coerceIn(0f, 1f)
                        sin(local * Math.PI.toFloat())
                    }

                    val eased = easeOutBack(progress) * (1f + pulse * 0.16f)
                    val centre = Offset(tl.x + boxSize.width / 2f, tl.y + boxSize.height / 2f)

                    scale(scale = eased, pivot = centre) {
                        drawRoundRect(
                            color = lerp(
                                GameColors.RevealedCell,
                                GameColors.ValidWord,
                                pulse * 0.55f,
                            ),
                            topLeft = tl,
                            size = boxSize,
                            cornerRadius = corner,
                            alpha = progress,
                        )
                        val g = glyphs.getValue(pos)
                        drawText(
                            textLayoutResult = g,
                            topLeft = Offset(
                                x = tl.x + (boxSize.width - g.size.width) / 2f,
                                y = tl.y + (boxSize.height - g.size.height) / 2f,
                            ),
                            alpha = progress,
                        )
                    }
                }
            }
        },
    )
}

/**
 * Overshoot easing: 0 -> slightly past 1 -> settle.
 *
 * The tile grows a little larger than its final size before coming back, which
 * is what makes a reveal feel like it lands rather than merely appears. Applied
 * to scale only; alpha stays linear, because overshooting opacity just looks
 * like a flicker.
 */
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}
