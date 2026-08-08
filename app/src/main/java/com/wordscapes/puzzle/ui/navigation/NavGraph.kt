package com.wordscapes.puzzle.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wordscapes.puzzle.ui.game.GameScreen
import com.wordscapes.puzzle.ui.home.HomeScreen
import com.wordscapes.puzzle.ui.levelselect.LevelSelectScreen

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
    }
}
