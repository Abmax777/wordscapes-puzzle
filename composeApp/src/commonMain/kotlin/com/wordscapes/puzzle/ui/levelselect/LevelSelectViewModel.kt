package com.wordscapes.puzzle.ui.levelselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordscapes.puzzle.domain.repository.LevelCatalog
import com.wordscapes.puzzle.domain.repository.ProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One tile in the grid. */
data class LevelCard(
    val id: Int,
    val isUnlocked: Boolean,
    val isCompleted: Boolean,
)

data class LevelSelectUiState(
    val levels: List<LevelCard> = emptyList(),
    val completedCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Collects progress rather than reading it once, so completing a level elsewhere
 * updates this list. Unlock state is derived, never persisted.
 */
class LevelSelectViewModel(
    private val levelCatalog: LevelCatalog,
    private val progressStore: ProgressStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LevelSelectUiState())
    val uiState: StateFlow<LevelSelectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val orderedIds = try {
                levelCatalog.getLevels().map { it.id }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Could not load levels")
                }
                return@launch
            }

            // Never returns: lives as long as the ViewModel.
            progressStore.progress.collect { progress ->
                _uiState.update {
                    it.copy(
                        levels = orderedIds.map { id ->
                            LevelCard(
                                id = id,
                                isUnlocked = progress.isUnlocked(id, orderedIds),
                                isCompleted = progress.isCompleted(id),
                            )
                        },
                        completedCount = progress.completedCount,
                        isLoading = false,
                        error = null,
                    )
                }
            }
        }
    }
}
