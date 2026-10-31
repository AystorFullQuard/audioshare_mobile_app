package mme.corp.audioshare.ui.screens.navigation

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
import mme.corp.audioshare.ui.screens.home.HomeScreen
import mme.corp.audioshare.ui.screens.login.LoginScreen
import mme.corp.audioshare.ui.screens.profile.ProfileScreen

/** Home is always reachable offline. Protected room screens require a bootstrapped session. */
@Composable
fun AudioShareNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val application = LocalContext.current.applicationContext as AudioShareApplication
    val accessToken by application.container.accessTokenState.collectAsStateWithLifecycle()
    val confirmedPresence by application.container.presenceHeartbeatCoordinator
        .confirmedPresence.collectAsStateWithLifecycle()
    val runtimeReady = !accessToken.isNullOrBlank() && confirmedPresence != null
    val route = navController.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(route, runtimeReady) {
        if (shouldReturnHome(route, runtimeReady)) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onLogin = {
                    navController.navigate(Screen.Login.route) { launchSingleTop = true }
                },
                onNavigateRooms = {
                    if (runtimeReady) {
                        navController.navigate(Screen.Rooms.route) { launchSingleTop = true }
                    }
                },
                onOpenProfile = {
                    if (!accessToken.isNullOrBlank()) {
                        navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                    } else {
                        navController.navigate(Screen.Login.route) { launchSingleTop = true }
                    }
                }
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
        roomsDestinations(
            navController = navController,
            coordinator = application.container.roomSessionCoordinator,
            calls = application.container.onlineCallCoordinator,
            invites = application.container.inviteRepository,
            isStartupRuntimeReady = { runtimeReady }
        )
    }
}

