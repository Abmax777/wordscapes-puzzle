package com.wordscapes.puzzle.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordscapes.puzzle.BuildConfig
import com.wordscapes.puzzle.ui.theme.SkyBottom
import com.wordscapes.puzzle.ui.theme.SkyTop
import com.wordscapes.puzzle.ui.theme.WordscapesTheme
import kotlinx.coroutines.delay

/**
 * Entry screen — sky gradient background, title, single PLAY button.
 *
 * Animation notes (Compose-specific idioms):
 *
 * - [animateFloatAsState] is a one-shot, state-driven animation driven by a
 *   Boolean toggle. There is no animation object to manage; recomposition
 *   triggered by the state change is what kicks it off. The `label` parameter
 *   is mandatory in Compose 1.5+ and used by the Layout Inspector.
 *
 * - [LaunchedEffect(Unit)] starts a coroutine scoped to this composable's
 *   lifecycle. The `Unit` key means "run once on entry, cancel on exit" —
 *   the same guarantee as onStart/onStop in a Fragment. We delay 80 ms so
 *   the animation is visible on a fresh cold-start render rather than
 *   being skipped by the first frame.
 *
 * - Both alpha and scale animate in parallel — no need to sequence them
 *   because animateFloatAsState is launched by the same state flip.
 */
@Composable
fun HomeScreen(
    onPlayClicked: () -> Unit,
    onSandboxClicked: () -> Unit = {},
) {
    var entered by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue   = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label         = "home_alpha",
    )
    val scale by animateFloatAsState(
        targetValue   = if (entered) 1f else 0.88f,
        animationSpec = tween(durationMillis = 600),
        label         = "home_scale",
    )

    LaunchedEffect(Unit) {
        delay(80)
        entered = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(SkyTop, SkyBottom)),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .alpha(alpha)
                .scale(scale)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 48sp with 4sp tracking overflows a 360dp screen and wraps to
            // "WORDSCAPE / S". maxLines pins the single-line intent; the size
            // and tracking are dialled back until it fits with margin to
            // spare, including at raised system font scales.
            Text(
                text      = "WORDSCAPES",
                style     = MaterialTheme.typography.displayLarge.copy(
                    fontWeight    = FontWeight.Black,
                    fontSize      = 38.sp,
                    letterSpacing = 2.sp,
                ),
                color     = Color.White,
                textAlign = TextAlign.Center,
                maxLines  = 1,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text      = "Find the hidden words",
                style     = MaterialTheme.typography.bodyLarge,
                color     = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(72.dp))

            Button(
                onClick = onPlayClicked,
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.58f)
                    .height(56.dp),
                shape  = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = SkyTop,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            ) {
                Text(
                    text  = "PLAY",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 3.sp,
                    ),
                )
            }

            // ── DEV ONLY: remove before submitting (Day 6 cleanup) ──────────
            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onSandboxClicked) {
                    Text(
                        text  = "wheel sandbox",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D3B6B)
@Composable
private fun HomeScreenPreview() {
    WordscapesTheme {
        HomeScreen(onPlayClicked = {})
    }
}
