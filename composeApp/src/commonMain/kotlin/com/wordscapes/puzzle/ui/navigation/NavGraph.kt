package com.wordscapes.puzzle.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wordscapes.puzzle.ui.game.GameScreen
import com.wordscapes.puzzle.ui.game.GameViewModel
import com.wordscapes.puzzle.ui.home.HomeScreen
import com.wordscapes.puzzle.ui.levelselect.LevelSelectScreen
import com.wordscapes.puzzle.ui.pause.PauseDialog

private const val TRANSITION_MS = 300

/**
 * Root nav host; all back-stack policy lives here. Auto-advance replaces the Game
 * entry rather than pushing, or Back would walk every solved board.
 */
@Composable
fun WordscapesNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Home,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(TRANSITION_MS),
            ) + fadeIn(animationSpec = tween(TRANSITION_MS))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(TRANSITION_MS),
            ) + fadeOut(animationSpec = tween(TRANSITION_MS))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(TRANSITION_MS),
            ) + fadeIn(animationSpec = tween(TRANSITION_MS))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(TRANSITION_MS),
            ) + fadeOut(animationSpec = tween(TRANSITION_MS))
        },
    ) {
        composable<Destination.Home> {
            HomeScreen(
                onPlayClicked = { navController.navigate(Destination.LevelSelect) },
                onContinueClicked = { levelId ->
                    // LevelSelect underneath first, so the stack shape is the same
                    // however the level was entered.
                    navController.navigate(Destination.LevelSelect)
                    navController.navigate(Destination.Game(levelId))
                },
            )
        }

        composable<Destination.LevelSelect> {
            LevelSelectScreen(
                onLevelClicked = { id -> navController.navigate(Destination.Game(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Destination.Game> { backStackEntry ->
            val destination: Destination.Game = backStackEntry.toRoute()
            GameScreen(
                onBack = { navController.popBackStack() },
                onPause = { navController.navigate(Destination.Pause(destination.levelId)) },
                onAdvanceToLevel = { nextId ->
                    navController.navigate(Destination.Game(nextId)) {
                        // Replace, do not push. See the class comment: pushing
                        // accumulates one Game entry per level completed.
                        popUpTo(destination) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onFinishedFinalLevel = {
                    // Never strand the player on a finished board.
                    navController.popBackStack(Destination.LevelSelect, inclusive = false)
                },
            )
        }

        // dialog<T>: renders on top of Game, so Back dismisses only this.
        dialog<Destination.Pause> { entry ->
            val pause: Destination.Pause = entry.toRoute()

            // The Game entry's ViewModel, not a second one scoped to this dialog.
            val gameEntry = remember(entry) {
                navController.getBackStackEntry(Destination.Game(pause.levelId))
            }
            val gameViewModel: GameViewModel = koinViewModel(viewModelStoreOwner = gameEntry)
            val state by gameViewModel.uiState.collectAsStateWithLifecycle()

            PauseDialog(
                levelId = pause.levelId,
                wordsFound = state.wordsFound,
                wordsTotal = state.wordsTotal,
                onResume = { navController.popBackStack() },
                onRestart = {
                    // A fresh entry means a fresh SavedStateHandle, so state clears
                    // without a reset() that only one caller would use.
                    navController.navigate(Destination.Game(pause.levelId)) {
                        popUpTo(Destination.Game(pause.levelId)) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onQuitToLevels = {
                    navController.popBackStack(Destination.LevelSelect, inclusive = false)
                },
            )
        }
    }
}
