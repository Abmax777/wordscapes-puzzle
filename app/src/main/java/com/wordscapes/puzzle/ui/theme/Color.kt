package com.wordscapes.puzzle.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Sky gradient ──────────────────────────────────────────────────────────
// The two ends of the vertical gradient used on Home and (later) Gameplay.
val SkyTop    = Color(0xFF0D3B6B) // deep indigo-blue, top of screen
val SkyBottom = Color(0xFF1E6FA8) // lighter horizon blue

// ─── Material3 seed palette (hand-picked to complement the sky) ─────────────
val Blue80       = Color(0xFF9ECAFF)
val Blue40       = Color(0xFF0061A4)
val BlueGrey80   = Color(0xFFBBC7DB)
val BlueGrey40   = Color(0xFF44546A)
val Teal80       = Color(0xFF72D4D1)
val Teal40       = Color(0xFF006A67)

// ─── Game-specific semantic tokens ──────────────────────────────────────────
// Use these directly in composables rather than going through MaterialTheme,
// because they have no M3 equivalent and must not shift with dynamic colour.
object GameColors {
    /** Letter tile fill — warm cream, high contrast against the sky disc */
    val LetterTile = Color(0xFFFFF9C4)

    /** Text/icon colour on a letter tile */
    val LetterText = Color(0xFF3E2723)

    /** Ring drawn around a selected / in-flight letter */
    val LetterSelected = Color(0xFF29B6F6)

    /** The swipe path line: segments + live finger segment */
    val SelectionLine = Color(0xFF29B6F6)

    /** Grid cell reveal — word in the crossword grid */
    val ValidWord = Color(0xFF43A047)

    /** Bonus / extra word found (not in the grid) */
    val BonusWord = Color(0xFFAB47BC)

    /** Invalid word submitted — drives the shake animation */
    val InvalidWord = Color(0xFFEF5350)

    /** Already-found word resubmitted — third distinct feedback state */
    val AlreadyFound = Color(0xFFFFA726)

    /** The dark disc behind the letter wheel */
    val WheelDisc = Color(0xFF0D1B4B)

    /** Revealed grid cell background */
    val RevealedCell = Color(0xFFFFFDE7)

    /** Unrevealed grid cell background */
    val UnrevealedCell = Color(0xFFE3F2FD)
}
