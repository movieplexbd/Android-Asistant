package com.jarvis.ceotitan.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jarvis.ceotitan.ui.screens.*

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Listening : Screen("listening")
    data object Chat : Screen("chat")
    data object Memory : Screen("memory")
    data object Automation : Screen("automation")
    data object Business : Screen("business")
    data object Settings : Screen("settings")
    data object Permissions : Screen("permissions")
    data object OfflineCommands : Screen("offline_commands")
}

@Composable
fun JarvisNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(300)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(200)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(200)
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(300)
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(200)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(200)
            )
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.Listening.route) {
            ListeningScreen(navController = navController)
        }
        composable(Screen.Chat.route) {
            ChatScreen(navController = navController)
        }
        composable(Screen.Memory.route) {
            MemoryScreen(navController = navController)
        }
        composable(Screen.Automation.route) {
            AutomationScreen(navController = navController)
        }
        composable(Screen.Business.route) {
            BusinessScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(navController = navController)
        }
        composable(Screen.OfflineCommands.route) {
            OfflineCommandsScreen(navController = navController)
        }
    }
}
