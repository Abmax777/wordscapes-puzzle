package com.wordscapes.puzzle.ui.levelselect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordscapes.puzzle.ui.theme.GameColors
import com.wordscapes.puzzle.ui.theme.SkyBottom
import com.wordscapes.puzzle.ui.theme.SkyTop

@Composable
fun LevelSelectScreen(
    onLevelClicked: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: LevelSelectViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Text("Back", color = Color.White.copy(alpha = 0.85f))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        text = "SELECT LEVEL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (state.levels.isNotEmpty()) {
                        Text(
                            text = "${state.completedCount} of ${state.levels.size} complete",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }

                state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error ?: "", color = Color.White)
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.levels, key = { it.id }) { card ->
                        // Three states, distinguishable without colour alone:
                        // completed carries a tick, locked a padlock and no number.
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    when {
                                        card.isCompleted -> GameColors.ValidWord.copy(alpha = 0.32f)
                                        card.isUnlocked -> Color.White.copy(alpha = 0.16f)
                                        else -> Color.Black.copy(alpha = 0.20f)
                                    },
                                )
                                .then(
                                    if (card.isCompleted) {
                                        Modifier.border(
                                            BorderStroke(1.5.dp, GameColors.ValidWord),
                                            RoundedCornerShape(16.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                // Locked tiles take no click at all: an affordance
                                // that does nothing is worse than none.
                                .then(
                                    if (card.isUnlocked) {
                                        Modifier.clickable { onLevelClicked(card.id) }
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (card.isUnlocked) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${card.id}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    if (card.isCompleted) {
                                        Text(
                                            text = "✓",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = GameColors.ValidWord,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "🔒",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
