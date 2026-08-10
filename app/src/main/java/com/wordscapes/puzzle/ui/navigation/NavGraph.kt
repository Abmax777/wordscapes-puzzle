package com.wordscapes.puzzle.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
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
 * Root navigation host.
 *
 * All back-stack policy lives here rather than inside screens, so there is one
 * place to reason about what Back does at every node.
 *
 * The auto-advance rule is the one worth reading. On completing a level the
 * app navigates to the next Game destination while popping the current one
 * inclusively — replacing rather than pushing. Pushing would stack a Game
 * entry per level completed, so a player who finished five levels and pressed
 * Back would walk backwards through all five, each already solved.
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
                    // Push LevelSelect underneath first, so Back from a
                    // continued level lands on the level list rather than
                    // jumping straight out to Home. Without this the back
                    // stack shape depends on how the player entered the level.
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
                    // Final level done — return to the level list rather than
                    // leaving the player on a completed board with no exit.
                    navController.popBackStack(Destination.LevelSelect, inclusive = false)
                },
            )
        }

        // dialog<T>, not composable<T>: this renders ON TOP of Game rather than
        // replacing it, so Game stays composed and Back dismisses only this.
        dialog<Destination.Pause> { entry ->
            val pause: Destination.Pause = entry.toRoute()

            // Reach the Game entry's ViewModel rather than letting
            // hiltViewModel() build a second one scoped to this dialog. A
            // fresh instance would report 0 of N words and, worse, would run
            // its own submission channel against the same SavedStateHandle.
            val gameEntry = remember(entry) {
                navController.getBackStackEntry(Destination.Game(pause.levelId))
            }
            val gameViewModel: GameViewModel = hiltViewModel(gameEntry)
            val state by gameViewModel.uiState.collectAsStateWithLifecycle()

            PauseDialog(
                levelId = pause.levelId,
                wordsFound = state.wordsFound,
                wordsTotal = state.wordsTotal,
                onResume = { navController.popBackStack() },
                onRestart = {
                    // Replace the Game entry with a fresh one. The new entry
                    // gets a new SavedStateHandle, so revealed words and bonus
                    // words reset without the ViewModel needing a reset method
                    // that only this one caller would ever use.
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
