package com.wordscapes.puzzle.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordscapes.puzzle.domain.model.WordResult
import com.wordscapes.puzzle.ui.game.grid.CrosswordGrid
import com.wordscapes.puzzle.ui.game.wheel.LetterWheel
import com.wordscapes.puzzle.ui.game.wheel.WheelGestureState
import com.wordscapes.puzzle.ui.theme.GameColors
import com.wordscapes.puzzle.ui.theme.SkyBottom
import com.wordscapes.puzzle.ui.theme.SkyTop
import kotlinx.coroutines.delay
import kotlin.math.sin

/** How long the completion message sits before the next level loads. */
private const val AUTO_ADVANCE_DELAY_MS = 1100L

/** How long a validation message stays on screen before fading out. */
private const val FEEDBACK_LINGER_MS = 1400L

/** Shake duration for a rejected word. */
private const val SHAKE_MS = 420

/** Full left-right cycles within that duration. */
private const val SHAKE_CYCLES = 3f

/**
 * The gameplay screen.
 *
 * Observes one [GameUiState] via `collectAsStateWithLifecycle`, which stops
 * collecting when the screen is not at least STARTED. Plain `collectAsState`
 * would keep the flow active while the app is backgrounded — harmless here,
 * but the habit matters as soon as a flow does real work.
 *
 * Navigation is not performed here. The screen reports *what happened* through
 * callbacks and the nav graph decides where that leads, so back-stack policy
 * lives in one place rather than being scattered across screens.
 */
@Composable
fun GameScreen(
    onBack: () -> Unit,
    onPause: () -> Unit,
    onAdvanceToLevel: (Int) -> Unit,
    onFinishedFinalLevel: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Gesture state deliberately lives here and not in the ViewModel: it is
    // worthless across process death and would otherwise need serialising.
    val wheelState = remember { WheelGestureState() }
    var showBonusList by rememberSaveable { mutableStateOf(false) }

    // Keyed on submissionId, not on lastResult. Two identical rapid results
    // would not restart a LaunchedEffect keyed on the result itself, so the
    // second word's feedback would inherit the first one's remaining timer.
    LaunchedEffect(state.submissionId) {
        if (state.lastResult == null) return@LaunchedEffect
        delay(FEEDBACK_LINGER_MS)
        viewModel.consumeFeedback()
    }

    // Auto-advance. Keyed on isComplete so it fires once per completion rather
    // than on every recomposition, and the delay lets the final reveal land
    // before the screen changes.
    LaunchedEffect(state.isComplete, state.nextLevelId) {
        if (!state.isComplete) return@LaunchedEffect
        delay(AUTO_ADVANCE_DELAY_MS)
        val next = state.nextLevelId
        if (next != null) onAdvanceToLevel(next) else onFinishedFinalLevel()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom))),
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error ?: "",
                        color = GameColors.InvalidWord,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    TextButton(onClick = onBack) { Text("Back", color = Color.White) }
                }
            }

            else -> {
                val level = state.level ?: return@Box
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Opens the pause dialog rather than popping the
                        // level. System Back still pops, which is the correct
                        // platform behaviour; this is the in-game affordance.
                        TextButton(onClick = onPause) {
                            Text("Pause", color = Color.White.copy(alpha = 0.85f))
                        }
                        Text(
                            text = "Level ${level.id}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Text(
                                text = "${state.wordsFound}/${state.wordsTotal}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            BonusChip(
                                count = state.foundBonusWords.size,
                                onClick = { showBonusList = true },
                            )
                        }
                    }

                    CrosswordGrid(
                        level = level,
                        revealedWordIndices = state.revealedWordIndices,
                        isComplete = state.isComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.1f)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )

                    FeedbackBanner(state = state, modifier = Modifier.height(40.dp))

                    LetterWheel(
                        letters = level.letters,
                        state = wheelState,
                        onWordSubmitted = viewModel::submitWord,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )

                    Spacer(Modifier.height(8.dp))
                }

                if (showBonusList) {
                    BonusWordsDialog(
                        words = state.foundBonusWords,
                        onDismiss = { showBonusList = false },
                    )
                }
            }
        }
    }
}

/**
 * Tappable count of bonus words found so far.
 *
 * Bonus words were being collected with nowhere to see them, which makes the
 * bonus feedback feel like it leads nowhere. Dimmed and inert at zero so it
 * never invites a tap that opens an empty dialog.
 */
@Composable
private fun BonusChip(
    count: Int,
    onClick: () -> Unit,
) {
    val enabled = count > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) GameColors.BonusWord.copy(alpha = 0.30f)
                else Color.White.copy(alpha = 0.10f),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$count bonus",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun BonusWordsDialog(
    words: Set<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Text(
                text = "Bonus words (${words.size})",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (words.isEmpty()) {
                Text("None yet. Swipe a real word that is not in the grid.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    words.sorted().forEach { word ->
                        Text(
                            text = word,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = GameColors.BonusWord,
                        )
                    }
                }
            }
        },
    )
}

/**
 * The three graded validation feedbacks, plus already-found as a fourth.
 *
 * Colour only for now. The shake, the tile reveal and the capture scale are
 * Day 6 — and when they arrive they must be keyed on
 * [GameUiState.submissionId], not on the result, or two identical rapid
 * submissions will produce a single animation.
 */
@Composable
private fun FeedbackBanner(
    state: GameUiState,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val shake = remember { Animatable(0f) }

    // Keyed on submissionId, NOT on lastResult.
    //
    // Two rejected words in a row are frequently the same string, and Compose
    // would see an unchanged key and skip re-running this — so the second
    // rejection would produce no shake at all. A monotonic counter makes every
    // submission distinct. This is the reason GameUiState carries that field.
    LaunchedEffect(state.submissionId) {
        if (state.lastResult !is WordResult.Invalid) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        shake.snapTo(0f)
        shake.animateTo(1f, tween(SHAKE_MS, easing = LinearEasing))
    }

    // Every branch carries a label. An earlier version showed the bare word
    // for Invalid, which left a red word on screen with nothing saying why it
    // was rejected -- indistinguishable, at a glance, from one accepted.
    val (text, target) = when {
        state.isComplete -> "LEVEL COMPLETE" to GameColors.ValidWord
        else -> when (val r = state.lastResult) {
            null -> "" to Color.Transparent
            is WordResult.GridWord ->
                r.word to GameColors.ValidWord
            is WordResult.BonusWord ->
                "${r.word}  ·  bonus word" to GameColors.BonusWord
            is WordResult.AlreadyFound ->
                "${r.word}  ·  already found" to GameColors.AlreadyFound
            is WordResult.Invalid ->
                "${r.word}  ·  not a word" to GameColors.InvalidWord
        }
    }
    val color by animateColorAsState(targetValue = target, label = "feedback_color")
    val amplitudePx = with(density) { 9.dp.toPx() }

    // LEVEL COMPLETE lands rather than fades in.
    val completeScale = remember { Animatable(1f) }
    LaunchedEffect(state.isComplete) {
        if (!state.isComplete) return@LaunchedEffect
        completeScale.snapTo(0.65f)
        completeScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.graphicsLayer {
                // Read inside graphicsLayer rather than in the composable body:
                // the lambda runs at draw time, so 25 animation frames cost 25
                // redraws instead of 25 recompositions of the whole banner.
                translationX = damped(shake.value, amplitudePx)
                scaleX = completeScale.value
                scaleY = completeScale.value
            },
        )
    }
}

/**
 * Damped oscillation: a few decreasing swings that settle at zero.
 *
 * Amplitude falls off linearly with progress so the motion ends still, rather
 * than being cut off mid-swing — a shake that stops abruptly reads as a
 * dropped frame.
 */
private fun damped(t: Float, amplitude: Float): Float {
    if (t <= 0f || t >= 1f) return 0f
    return amplitude * sin(t * SHAKE_CYCLES * 2f * Math.PI.toFloat()) * (1f - t)
}
