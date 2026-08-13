package com.wordscapes.puzzle.data.level

import kotlinx.serialization.Serializable

/**
 * Wire format for `assets/levels.json`. No logic — everything derived happens in
 * [LevelMapper], so this stays an obvious transcription of the file.
 */
@Serializable
data class LevelsFileDto(
    val levels: List<LevelDto>,
)

@Serializable
data class LevelDto(
    val id: Int,
    val letters: List<String>,
    val gridWidth: Int,
    val gridHeight: Int,
    val words: List<PlacedWordDto>,
)

@Serializable
data class PlacedWordDto(
    val word: String,
    val row: Int,
    val col: Int,
    val horizontal: Boolean,
)
