package mme.corp.audioshare.ui.screens.navigation

sealed class Screen(val route: String) {

    data object Splash : Screen("splash")

    data object Login : Screen("login")

    data object Bootstrap : Screen("bootstrap")

    data object Home : Screen("home")

    data object Room : Screen("room")
}