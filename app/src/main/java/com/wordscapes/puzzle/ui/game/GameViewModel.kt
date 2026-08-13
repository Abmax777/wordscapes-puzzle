package com.wordscapes.puzzle.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordscapes.puzzle.domain.model.WordResult
import com.wordscapes.puzzle.domain.repository.LevelCatalog
import com.wordscapes.puzzle.domain.repository.ProgressStore
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
 * Owns one level's durable state. Submissions are serialised through a Channel:
 * validation suspends, so concurrent swipes would read the same stale snapshot.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val levelCatalog: LevelCatalog,
    private val validateWord: ValidateWord,
    private val progressStore: ProgressStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /** Unlimited: a conflated channel would drop the middle of a fast sequence. */
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

    /** Non-blocking; the wheel never waits on validation. */
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

        // Snapshot before/after rather than a flag inside update{}, whose lambda
        // re-runs on CAS failure and would fire side effects twice.
        val before = _uiState.value

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

        // On the transition, not the state: otherwise every later submission writes again.
        if (!before.isComplete && _uiState.value.isComplete) {
            progressStore.markCompleted(level.id)
        }
    }

    /** Clears feedback so it does not linger. */
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
        /** Matches the property name on Destination.Game, so tests can use a plain
         *  SavedStateHandle(mapOf(...)) instead of a real NavBackStackEntry. */
        const val ARG_LEVEL_ID = "levelId"
        const val KEY_REVEALED = "revealedWordIndices"
        const val KEY_BONUS = "foundBonusWords"
    }
}
