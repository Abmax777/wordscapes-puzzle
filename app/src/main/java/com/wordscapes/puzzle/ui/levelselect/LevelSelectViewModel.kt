package com.wordscapes.puzzle.ui.levelselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordscapes.puzzle.domain.repository.LevelCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LevelSelectUiState(
    val levelIds: List<Int> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Lists the available levels.
 *
 * Deliberately thin. Unlock gating and per-level completion marks come from
 * DataStore on Day 5; today every level is playable so the wheel and grid can
 * be exercised against any of them.
 */
@HiltViewModel
class LevelSelectViewModel @Inject constructor(
    private val levelCatalog: LevelCatalog,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LevelSelectUiState())
    val uiState: StateFlow<LevelSelectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val ids = levelCatalog.getLevels().map { it.id }
                _uiState.update { it.copy(levelIds = ids, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Could not load levels")
                }
            }
        }
    }
}
