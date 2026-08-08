package com.wordscapes.puzzle.ui.game.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wordscapes.puzzle.ui.theme.SkyTop
import com.wordscapes.puzzle.ui.theme.WordscapesTheme

/**
 * Development sandbox for the wheel. Not part of the shipped flow — it exists
 * so the wheel can be built and tuned on a screen with nothing else on it,
 * which is what the plan calls for and what makes a misbehaving swipe
 * unambiguous.
 *
 * Delete this screen and its nav entry before submitting, or leave it behind a
 * BuildConfig.DEBUG check. Either is defensible; leaving it reachable from a
 * release build is not.
 *
 * `rememberSaveable` rather than `remember` for the toggles: these survive
 * rotation, which matters because rotating while tuning is exactly what you'll
 * be doing. The distinction is worth internalising — `remember` survives
 * recomposition, `rememberSaveable` additionally survives configuration change
 * and process death by writing into the same bundle mechanism as
 * `SavedStateHandle`.
 */
@Composable
fun WheelSandboxScreen() {
    var letterCount by rememberSaveable { mutableStateOf(5) }
    var showHitTargets by rememberSaveable { mutableStateOf(true) }

    val letters = SAMPLE_LETTERS.take(letterCount)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyTop)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "WHEEL SANDBOX",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.height(8.dp))

        // weight(1f) rather than aspectRatio(1f): the wheel takes whatever
        // vertical space is left after the controls, and WheelGeometry sizes
        // the disc from the smaller dimension so it stays circular.
        //
        // aspectRatio(1f) resolves width first, so on a landscape screen it
        // asks for a square as tall as the screen is wide — which overflows
        // the column and, since Compose does not clip by default, paints over
        // everything else. That is exactly what happened on the automotive
        // emulator.
        LetterWheel(
            letters = letters,
            showHitTargets = showHitTargets,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(3, 5, 6, 7).forEach { n ->
                Button(
                    onClick = { letterCount = n },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (n == letterCount) Color.White else Color.White.copy(alpha = 0.18f),
                        contentColor =
                            if (n == letterCount) SkyTop else Color.White,
                    ),
                ) { Text("$n") }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Show hit targets",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Switch(
                checked = showHitTargets,
                onCheckedChange = { showHitTargets = it },
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Cyan rings are touch targets, not the drawn circle. " +
                "They should look noticeably too big.",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** Level 1's wheel, padded out so 6- and 7-letter layouts can be inspected. */
private val SAMPLE_LETTERS = listOf('S', 'I', 'T', 'A', 'R', 'E', 'N')

@Preview(showBackground = true, backgroundColor = 0xFF0D3B6B)
@Composable
private fun WheelSandboxPreview() {
    WordscapesTheme { WheelSandboxScreen() }
}
