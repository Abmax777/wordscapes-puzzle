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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordscapes.puzzle.ui.theme.SkyBottom
import com.wordscapes.puzzle.ui.theme.SkyTop
import com.wordscapes.puzzle.ui.theme.WordscapesTheme
import kotlinx.coroutines.delay

/** Title screen. Fade and scale on entry; Continue appears only once there is progress. */
@Composable
fun HomeScreen(
    onPlayClicked: () -> Unit,
    onContinueClicked: (Int) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onPlayClicked = onPlayClicked,
        onContinueClicked = onContinueClicked,
    )
}

/** Stateless half: a composable calling hiltViewModel() cannot be previewed. */
@Composable
private fun HomeContent(
    state: HomeUiState,
    onPlayClicked: () -> Unit,
    onContinueClicked: (Int) -> Unit,
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
            // 48sp/4sp tracking wrapped to "WORDSCAPE / S" on a 360dp screen.
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
                    text  = if (state.hasProgress) "PLAY" else "START",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 3.sp,
                    ),
                )
            }

            // On a fresh install Continue would be indistinguishable from Play.
            val continueId = state.continueLevelId
            if (state.hasProgress && continueId != null) {
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = { onContinueClicked(continueId) }) {
                    Text(
                        text = if (state.allComplete) {
                            "Replay level $continueId"
                        } else {
                            "Continue · level $continueId"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Text(
                    text = "${state.completedCount} of ${state.totalLevels} complete",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }

        }
    }
}

@Preview(name = "Fresh install", showBackground = true, backgroundColor = 0xFF0D3B6B)
@Composable
private fun HomeFreshPreview() {
    WordscapesTheme {
        HomeContent(
            state = HomeUiState(continueLevelId = 1, completedCount = 0, totalLevels = 15),
            onPlayClicked = {},
            onContinueClicked = {},
        )
    }
}

@Preview(name = "With progress", showBackground = true, backgroundColor = 0xFF0D3B6B)
@Composable
private fun HomeWithProgressPreview() {
    WordscapesTheme {
        HomeContent(
            state = HomeUiState(continueLevelId = 6, completedCount = 5, totalLevels = 15),
            onPlayClicked = {},
            onContinueClicked = {},
        )
    }
}
