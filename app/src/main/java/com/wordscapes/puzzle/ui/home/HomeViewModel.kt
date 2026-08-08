package com.wordscapes.puzzle.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Placeholder ViewModel for HomeScreen.
 *
 * Day 5 addition: inject ProgressRepository, expose the highest unlocked
 * level so the Home screen can show a "Continue" button pointing to the
 * right level instead of always going to Level Select.
 */
@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel()
