package mme.corp.audioshare.ui.screens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import mme.corp.audioshare.ui.screens.bootstrap.BootstrapScreen
import mme.corp.audioshare.ui.screens.home.HomeScreen
import mme.corp.audioshare.ui.screens.login.LoginScreen
import mme.corp.audioshare.ui.screens.splash.SplashScreen

@Composable
fun AudioShareNavHost(
    modifier: Modifier = Modifier
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {

        composable(Screen.Splash.route) {

            SplashScreen(

                onNavigateLogin = {

                    navController.navigate(Screen.Login.route) {

                        popUpTo(Screen.Splash.route) {
                            inclusive = true
                        }

                    }

                },

                onNavigateBootstrap = {

                    navController.navigate(Screen.Bootstrap.route) {

                        popUpTo(Screen.Splash.route) {
                            inclusive = true
                        }

                    }

                }

            )

        }

        composable(Screen.Login.route) {

            LoginScreen(

                onLoginSuccess = {

                    navController.navigate(Screen.Bootstrap.route) {

                        popUpTo(Screen.Login.route) {
                            inclusive = true
                        }

                    }

                }

            )

        }

        composable(Screen.Bootstrap.route) {

            BootstrapScreen(

                onFinished = {

                    navController.navigate(Screen.Home.route) {

                        popUpTo(Screen.Bootstrap.route) {
                            inclusive = true
                        }

                    }

                }

            )

        }

        composable(Screen.Home.route) {

            HomeScreen()

        }

    }

}