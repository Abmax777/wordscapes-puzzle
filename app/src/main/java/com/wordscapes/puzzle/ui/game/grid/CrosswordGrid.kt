package com.wordscapes.puzzle.ui.game.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.wordscapes.puzzle.domain.model.GridPosition
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.ui.theme.GameColors

/**
 * The crossword grid.
 *
 * Cells come from [Level.cells], which the data layer derived and validated at
 * parse time — so this composable never has to reason about whether a
 * placement is legal, only about drawing it.
 *
 * A cell shows its letter once *any* word passing through it has been
 * revealed. That is why [com.wordscapes.puzzle.domain.model.GridCell] carries
 * a set of word indices rather than one: revealing STAIR must also fill in the
 * shared letters of every word that crosses it, which is most of the
 * satisfaction of the mechanic.
 *
 * Unlike the wheel, [revealedWordIndices] is an ordinary parameter rather than
 * a deferred read. Reveals happen a handful of times per level, not 120 times
 * a second, so a recomposition per reveal is far cheaper than the indirection
 * needed to avoid it. The deferred-read pattern is a tool for the hot path,
 * not a default.
 */
@Composable
fun CrosswordGrid(
    level: Level,
    revealedWordIndices: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Box(
        modifier = modifier.drawWithCache {
            // Square cells sized by whichever axis is more constrained, so the
            // grid never distorts.
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
            // Every cell's glyph measured once per size change, not per reveal.
            val glyphs = level.cells.mapValues { (_, c) ->
                textMeasurer.measure(AnnotatedString(c.letter.toString()), style)
            }

            fun topLeftOf(pos: GridPosition) = Offset(
                x = originX + pos.col * cell + inset,
                y = originY + pos.row * cell + inset,
            )

            onDrawBehind {
                level.cells.forEach { (pos, gridCell) ->
                    val revealed = gridCell.wordIndices.any { it in revealedWordIndices }
                    val tl = topLeftOf(pos)

                    drawRoundRect(
                        color = if (revealed) GameColors.RevealedCell
                        else GameColors.UnrevealedCell.copy(alpha = 0.22f),
                        topLeft = tl,
                        size = boxSize,
                        cornerRadius = corner,
                    )

                    if (revealed) {
                        val g = glyphs.getValue(pos)
                        drawText(
                            textLayoutResult = g,
                            topLeft = Offset(
                                x = tl.x + (boxSize.width - g.size.width) / 2f,
                                y = tl.y + (boxSize.height - g.size.height) / 2f,
                            ),
                        )
                    }
                }
            }
        },
    )
}
