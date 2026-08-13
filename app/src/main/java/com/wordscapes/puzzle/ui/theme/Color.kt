package com.wordscapes.puzzle.ui.theme

import androidx.compose.ui.graphics.Color

// Two ends of the vertical sky gradient used behind every screen.
val SkyTop    = Color(0xFF0D3B6B)
val SkyBottom = Color(0xFF1E6FA8)

// Material3 seed palette, hand-picked to complement the sky.
val Blue80       = Color(0xFF9ECAFF)
val Blue40       = Color(0xFF0061A4)
val BlueGrey80   = Color(0xFFBBC7DB)
val BlueGrey40   = Color(0xFF44546A)
val Teal80       = Color(0xFF72D4D1)
val Teal40       = Color(0xFF006A67)

/**
 * Game tokens used directly rather than through MaterialTheme: they have no M3
 * equivalent and must not shift with dynamic colour.
 */
object GameColors {
    val LetterTile = Color(0xFFFFF9C4)
    val LetterText = Color(0xFF3E2723)
    val LetterSelected = Color(0xFF29B6F6)
    val SelectionLine = Color(0xFF29B6F6)

    // Four distinct submission outcomes; each also drives its own animation.
    val ValidWord = Color(0xFF43A047)
    val BonusWord = Color(0xFFAB47BC)
    val InvalidWord = Color(0xFFEF5350)
    val AlreadyFound = Color(0xFFFFA726)

    val WheelDisc = Color(0xFF0D1B4B)
    val RevealedCell = Color(0xFFFFFDE7)
    val UnrevealedCell = Color(0xFFE3F2FD)
}
