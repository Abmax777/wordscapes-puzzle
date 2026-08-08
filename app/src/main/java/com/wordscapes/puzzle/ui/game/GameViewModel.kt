package com.wordscapes.puzzle.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordscapes.puzzle.domain.model.WordResult
import com.wordscapes.puzzle.domain.repository.LevelCatalog
import com.wordscapes.puzzle.domain.usecase.ValidateWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the durable state of one level: which grid words are revealed and which
 * bonus words have been found.
 *
 * ## Submissions are serialised through a Channel
 *
 * The obvious implementation launches a coroutine per submitted word. That has
 * a real race: validation suspends on the dictionary lookup, so two swipes in
 * quick succession both read the same "revealed" snapshot before either
 * writes. Submit the same word twice fast enough and it resolves as a fresh
 * grid word both times — one of the rapid-swipe edge cases on the test list.
 *
 * Funnelling every submission through an unlimited Channel consumed by a
 * single coroutine makes handling strictly sequential: word N is fully
 * resolved and written before word N+1 is read. [submitWord] stays
 * non-blocking, so the wheel never waits on validation, and the buffer absorbs
 * a burst of fast swipes rather than dropping them.
 *
 * ## SavedStateHandle holds the level, not the gesture
 *
 * Revealed words and found bonus words are written to [SavedStateHandle] on
 * every change, so a level interrupted by process death resumes intact. They
 * are stored as primitive arrays because that is what a Bundle can carry — a
 * Set would not survive.
 *
 * Cross-session progress (highest level unlocked) is DataStore's job on Day 5,
 * not this class's. SavedStateHandle is for in-flight state only.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val levelCatalog: LevelCatalog,
    private val validateWord: ValidateWord,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /**
     * Unlimited so a burst of rapid swipes is buffered rather than dropped.
     * A conflated channel would discard the middle of a fast sequence.
     */
    private val submissions = Channel<String>(Channel.UNLIMITED)

    private val levelId: Int =
        savedStateHandle.get<Int>(ARG_LEVEL_ID)
            ?: error("GameViewModel requires a '$ARG_LEVEL_ID' argument")

    init {
        viewModelScope.launch { loadLevel() }
        viewModelScope.launch {
            for (word in submissions) handleSubmission(word)
        }
    }

    private suspend fun loadLevel() {
        try {
            val level = levelCatalog.getLevel(levelId)
            if (level == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Level $levelId not found")
                }
                return
            }
            _uiState.update {
                it.copy(
                    level = level,
                    revealedWordIndices = restoredRevealed(),
                    foundBonusWords = restoredBonus(),
                    nextLevelId = levelCatalog.nextLevelId(levelId),
                    isLoading = false,
                    error = null,
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, error = e.message ?: "Could not load level")
            }
        }
    }

    /** Non-blocking. The wheel never waits on validation. */
    fun submitWord(word: String) {
        submissions.trySend(word)
    }

    private suspend fun handleSubmission(word: String) {
        val snapshot = _uiState.value
        val level = snapshot.level ?: return

        val result = validateWord(
            word = word,
            level = level,
            revealedWordIndices = snapshot.revealedWordIndices,
            foundBonusWords = snapshot.foundBonusWords,
        )

        _uiState.update { current ->
            current.copy(
                revealedWordIndices = when (result) {
                    is WordResult.GridWord -> current.revealedWordIndices + result.wordIndex
                    else -> current.revealedWordIndices
                },
                foundBonusWords = when (result) {
                    is WordResult.BonusWord -> current.foundBonusWords + result.word
                    else -> current.foundBonusWords
                },
                lastResult = result,
                submissionId = current.submissionId + 1,
            )
        }
        persist()
    }

    /** Clears the feedback so a shake or toast does not linger. */
    fun consumeFeedback() {
        _uiState.update { it.copy(lastResult = null) }
    }

    // ── SavedStateHandle ────────────────────────────────────────────────────

    private fun restoredRevealed(): Set<Int> =
        savedStateHandle.get<IntArray>(KEY_REVEALED)?.toSet() ?: emptySet()

    private fun restoredBonus(): Set<String> =
        savedStateHandle.get<Array<String>>(KEY_BONUS)?.toSet() ?: emptySet()

    private fun persist() {
        val s = _uiState.value
        savedStateHandle[KEY_REVEALED] = s.revealedWordIndices.toIntArray()
        savedStateHandle[KEY_BONUS] = s.foundBonusWords.toTypedArray()
    }

    private companion object {
        /**
         * Matches the property name on Destination.Game. Type-safe navigation
         * stores route arguments under their property names, so reading the
         * key directly works and keeps this class testable with a plain
         * SavedStateHandle(mapOf(...)) instead of a real NavBackStackEntry.
         */
        const val ARG_LEVEL_ID = "levelId"
        const val KEY_REVEALED = "revealedWordIndices"
        const val KEY_BONUS = "foundBonusWords"
    }
}
