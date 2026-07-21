package mme.corp.audioshare.ui.screens.splash

sealed interface SplashDestination {

    data object Loading : SplashDestination

    data object Login : SplashDestination

    data object Bootstrap : SplashDestination
}