package com.wordscapes.puzzle.ui.home

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

data class HomeUiState(
    /** Where Continue should go: first unfinished level, or the last one. */
    val continueLevelId: Int? = null,
    val completedCount: Int = 0,
    val totalLevels: Int = 0,
) {
    /** Continue is only offered once there is something to continue from. */
    val hasProgress: Boolean get() = completedCount > 0 && continueLevelId != null
    val allComplete: Boolean get() = totalLevels > 0 && completedCount == totalLevels
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val levelCatalog: LevelCatalog,
    private val progressStore: ProgressStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val orderedIds = try {
                levelCatalog.getLevels().map { it.id }
            } catch (e: Exception) {
                return@launch      // Home still works without progress
            }
            progressStore.progress.collect { progress ->
                _uiState.update {
                    it.copy(
                        continueLevelId = progress.nextPlayableLevelId(orderedIds),
                        completedCount = progress.completedCount,
                        totalLevels = orderedIds.size,
                    )
                }
            }
        }
    }
}
