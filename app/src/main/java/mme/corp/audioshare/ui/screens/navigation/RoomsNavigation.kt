package mme.corp.audioshare.ui.screens.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import mme.corp.audioshare.room.RoomSessionCoordinator
import mme.corp.audioshare.ui.screens.rooms.RoomScreen
import mme.corp.audioshare.ui.screens.rooms.RoomsScreen

fun NavGraphBuilder.roomsDestinations(
    navController: NavHostController,
    coordinator: RoomSessionCoordinator,
    isStartupRuntimeReady: () -> Boolean = { true }
) {
    composable(Screen.Rooms.route) {
        if (isStartupRuntimeReady()) {
            RoomsScreen(
                coordinator = coordinator,
                onBack = navController::popBackStack,
                onNavigateRoom = navController::navigateToRoom,
                onNavigateRooms = navController::keepSingleRoomsDestination
            )
        }
    }

    composable(
        route = Screen.Room.route,
        arguments = listOf(
            navArgument(Screen.ROOM_ID_ARGUMENT) {
                type = NavType.StringType
            }
        )
    ) { entry ->
        if (isStartupRuntimeReady()) {
            RoomScreen(
                roomId = entry.arguments
                    ?.getString(Screen.ROOM_ID_ARGUMENT)
                    .orEmpty(),
                coordinator = coordinator,
                onBack = navController::popBackStack,
                onNavigateRooms = navController::returnToRooms
            )
        }
    }
}

private fun NavHostController.navigateToRoom(roomId: String) {
    navigate(Screen.Room.createRoute(roomId)) {
        launchSingleTop = true
    }
}

private fun NavHostController.keepSingleRoomsDestination() {
    navigate(Screen.Rooms.route) {
        popUpTo(Screen.Rooms.route) { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavHostController.returnToRooms() {
    if (popBackStack(Screen.Rooms.route, inclusive = false)) return

    navigate(Screen.Rooms.route) {
        popUpTo(Screen.Room.route) { inclusive = true }
        launchSingleTop = true
    }
}
