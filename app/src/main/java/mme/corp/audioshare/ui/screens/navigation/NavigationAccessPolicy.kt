package mme.corp.audioshare.ui.screens.navigation

/** Guest Home and Login remain accessible; rooms need an authenticated runtime. */
internal fun shouldReturnHome(route: String?, runtimeReady: Boolean): Boolean =
    !runtimeReady && (route == Screen.Rooms.route || route == Screen.Room.route)
