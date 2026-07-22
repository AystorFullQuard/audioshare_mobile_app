package mme.corp.audioshare.ui.screens.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import mme.corp.audioshare.ui.screens.bootstrap.BootstrapScreen
import mme.corp.audioshare.ui.screens.home.HomeScreen
import mme.corp.audioshare.ui.screens.login.LoginScreen
import mme.corp.audioshare.ui.screens.splash.SplashScreen

private const val TAG = "NavHost"

@Composable
fun AudioShareNavHost(
    modifier: Modifier = Modifier
) {

    Log.d(TAG, "==========================================")
    Log.d(TAG, "AudioShareNavHost created")

    val navController = rememberNavController()

    val backStackEntry =
        navController.currentBackStackEntryAsState()

    LaunchedEffect(backStackEntry.value) {

        Log.d(
            TAG,
            "Current Route = ${backStackEntry.value?.destination?.route}"
        )
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {

        composable(Screen.Splash.route) {

            Log.d(TAG, "Entered Splash screen")

            SplashScreen(

                onNavigateLogin = {

                    Log.i(TAG, "Navigation requested")
                    Log.i(TAG, "Splash -> Login")

                    try {

                        navController.navigate(Screen.Login.route) {

                            Log.d(TAG, "popUpTo(Splash)")

                            popUpTo(Screen.Splash.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }

                        Log.i(TAG, "Navigation to Login completed")

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Navigation Splash -> Login crashed",
                            e
                        )
                    }
                },

                onNavigateBootstrap = {

                    Log.i(TAG, "Navigation requested")
                    Log.i(TAG, "Splash -> Bootstrap")

                    try {

                        navController.navigate(Screen.Bootstrap.route) {

                            Log.d(TAG, "popUpTo(Splash)")

                            popUpTo(Screen.Splash.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }

                        Log.i(TAG, "Navigation to Bootstrap completed")

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Navigation Splash -> Bootstrap crashed",
                            e
                        )
                    }
                }

            )
        }

        composable(Screen.Login.route) {

            Log.d(TAG, "Entered Login screen")

            LoginScreen(

                onLoginSuccess = {

                    Log.i(TAG, "Login successful callback received")
                    Log.i(TAG, "Login -> Bootstrap")

                    try {

                        navController.navigate(Screen.Bootstrap.route) {

                            Log.d(TAG, "popUpTo(Login)")

                            popUpTo(Screen.Login.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }

                        Log.i(TAG, "Navigation to Bootstrap completed")

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Navigation Login -> Bootstrap crashed",
                            e
                        )
                    }
                }

            )
        }

        composable(Screen.Bootstrap.route) {

            Log.d(TAG, "Entered Bootstrap screen")

            BootstrapScreen(

                onFinished = {

                    Log.i(TAG, "Bootstrap finished")
                    Log.i(TAG, "Bootstrap -> Home")

                    try {

                        navController.navigate(Screen.Home.route) {

                            Log.d(TAG, "popUpTo(Bootstrap)")

                            popUpTo(Screen.Bootstrap.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }

                        Log.i(TAG, "Navigation to Home completed")

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Navigation Bootstrap -> Home crashed",
                            e
                        )
                    }
                }

            )
        }

        composable(Screen.Home.route) {

            Log.d(TAG, "Entered Home screen")

            HomeScreen()
        }
    }

    Log.d(TAG, "NavHost composition finished")
}