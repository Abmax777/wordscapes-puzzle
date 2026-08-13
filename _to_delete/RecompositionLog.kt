package com.wordscapes.puzzle.ui.debug

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import com.wordscapes.puzzle.BuildConfig

/**
 * TEMPORARY diagnostic. Delete before feature freeze.
 *
 * Logs once per successful recomposition of the composable it is placed in.
 * `SideEffect` runs after a composition commits, so one line per line of
 * output equals one recomposition — skipped compositions produce nothing,
 * which is exactly the distinction we want to measure.
 *
 * Deliberately logging rather than rendering a counter on screen. Displaying a
 * composable's own recomposition count inside itself is a feedback loop: the
 * counter write invalidates the composable, which recomposes, which writes
 * again. Logcat has no such problem.
 *
 *     adb logcat -c
 *     # ...swipe continuously for ~5s without lifting...
 *     adb logcat -d -s RECOMP | awk '{print $NF}' | sort | uniq -c | sort -rn
 */
/*
 * @NonRestartableComposable is load-bearing, not decoration.
 *
 * Without it this function gets its own restart group. Its only parameter is a
 * constant String, which is stable, so Compose skips it on every recomposition
 * after the first — and the SideEffect never runs again. The probe reports 1
 * and then goes silent regardless of what the app is doing, which reads as
 * "nothing recomposes" rather than as a broken measurement.
 *
 * The annotation suppresses the restart group, so the body executes as part of
 * the caller's recomposition every time.
 */
@Composable
@NonRestartableComposable
fun LogRecomposition(tag: String) {
    SideEffect {
        if (BuildConfig.DEBUG) Log.d("RECOMP", tag)
    }
}
