package com.wordscapes.puzzle.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wordscapes.puzzle.ui.home.HomeScreen

private const val TRANSITION_MS = 300

/**
 * Root navigation host.
 *
 * Design decisions captured here:
 *
 * - Pause is a dialog destination (not a screen), so it sits inside the Game
 *   composable and back just dismisses the dialog without popping the Game
 *   entry. Wired on Day 5.
 *
 * - Auto-advance on level completion uses navController.navigate(Game(nextId))
 *   { popUpTo(Game(currentId)) { inclusive = true } } so the back stack never
 *   accumulates duplicate Game entries. Wired on Day 4.
 *
 * - Every ViewModel is scoped to its backstack entry via hiltViewModel() with
 *   no explicit ViewModelStoreOwner override, which is the correct default.
 */
@Composable
fun WordscapesNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController    = navController,
        startDestination = Destination.Home,
        // Forward navigation: new screen slides in from the right
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec  = tween(TRANSITION_MS),
            ) + fadeIn(animationSpec = tween(TRANSITION_MS))
        },
        // Forward exit: old screen slides out to the left (at one-third speed
        // so the incoming screen has room to establish itself)
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec = tween(TRANSITION_MS),
            ) + fadeOut(animationSpec = tween(TRANSITION_MS))
        },
        // Back navigation: returning screen slides in from the left
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec  = tween(TRANSITION_MS),
            ) + fadeIn(animationSpec = tween(TRANSITION_MS))
        },
        // Back exit: current screen slides out to the right
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(TRANSITION_MS),
            ) + fadeOut(animationSpec = tween(TRANSITION_MS))
        },
    ) {
        composable<Destination.Home> {
            HomeScreen(
                onPlayClicked = { navController.navigate(Destination.LevelSelect) },
            )
        }

        // ── Day 1: replace placeholder with LevelSelectScreen + LevelSelectViewModel
        composable<Destination.LevelSelect> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Level Select · wired Day 1")
            }
        }

        // ── Day 4: replace placeholder with GameScreen + GameViewModel
        composable<Destination.Game> { backStackEntry ->
            val dest: Destination.Game = backStackEntry.toRoute()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Level ${dest.levelId} · wired Day 4")
            }
        }
    }
}
