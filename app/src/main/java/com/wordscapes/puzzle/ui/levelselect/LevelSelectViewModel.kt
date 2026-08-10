package com.wordscapes.puzzle.ui.levelselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordscapes.puzzle.domain.repository.LevelCatalog
import com.wordscapes.puzzle.domain.repository.ProgressStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
 * Lists levels with their unlock and completion state.
 *
 * Collects [ProgressStore.progress] rather than reading it once, so finishing
 * a level in Game updates this list without either screen knowing the other
 * exists. That is the reason progress is exposed as a Flow at all.
 *
 * Unlock state is derived here from the completed set plus the level ordering,
 * never persisted. See GameProgress for why.
 */
@HiltViewModel
class LevelSelectViewModel @Inject constructor(
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

            // collect never returns; the coroutine lives as long as the
            // ViewModel and re-emits whenever DataStore changes.
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
