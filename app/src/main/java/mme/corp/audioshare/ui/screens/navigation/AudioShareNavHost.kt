package mme.corp.audioshare.ui.screens.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import mme.corp.audioshare.AudioShareApplication
import mme.corp.audioshare.ui.screens.bootstrap.BootstrapScreen
import mme.corp.audioshare.ui.screens.home.HomeScreen
import mme.corp.audioshare.ui.screens.login.LoginScreen
import mme.corp.audioshare.ui.screens.splash.SplashScreen

private const val TAG = "NavHost"

@Composable
fun AudioShareNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val application =
        LocalContext.current.applicationContext as AudioShareApplication
    val accessToken by application.container.accessTokenState
        .collectAsStateWithLifecycle()
    val confirmedPresence by application.container
        .presenceHeartbeatCoordinator
        .confirmedPresence
        .collectAsStateWithLifecycle()
    val runtimeReady = !accessToken.isNullOrBlank() && confirmedPresence != null
    val backStackEntry = navController.currentBackStackEntryAsState()
    val route = backStackEntry.value?.destination?.route

    LaunchedEffect(route, runtimeReady) {
        Log.d(TAG, "Current Route = $route")

        if (shouldRestartAtSplash(route, runtimeReady)) {
            navController.restartAtSplash()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateBootstrap = {
                    navController.navigate(Screen.Bootstrap.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Bootstrap.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Bootstrap.route) {
            BootstrapScreen(
                onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Bootstrap.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            if (runtimeReady) {
                HomeScreen(
                    onNavigateRooms = {
                        navController.navigate(Screen.Rooms.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        roomsDestinations(
            navController = navController,
            coordinator = application.container.roomSessionCoordinator,
            isStartupRuntimeReady = { runtimeReady }
        )
    }
}

internal fun shouldRestartAtSplash(
    route: String?,
    runtimeReady: Boolean
): Boolean = !runtimeReady && when (route) {
    Screen.Home.route,
    Screen.Rooms.route,
    Screen.Room.route -> true
    else -> false
}

private fun NavHostController.restartAtSplash() {
    navigate(Screen.Splash.route) {
        popUpTo(Screen.Home.route) { inclusive = true }
        launchSingleTop = true
    }
}
