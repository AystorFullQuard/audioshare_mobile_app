package mme.corp.audioshare.ui.screens.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {

    @Test
    fun roomsRouteIsStable() {
        assertEquals("rooms", Screen.Rooms.route)
    }

    @Test
    fun roomRouteUsesOnlyRoomIdArgument() {
        assertEquals("room/{roomId}", Screen.Room.route)
        assertEquals("room/room-123", Screen.Room.createRoute("  room-123  "))
    }

    @Test
    fun bootstrapRestartsAtSplashOnlyWhenAuthenticationIsLost() {
        assertTrue(
            shouldRestartBootstrapAtSplash(
                Screen.Bootstrap.route,
                hasAccessToken = false
            )
        )
        assertFalse(
            shouldRestartBootstrapAtSplash(
                Screen.Bootstrap.route,
                hasAccessToken = true
            )
        )
        assertFalse(
            shouldRestartBootstrapAtSplash(
                Screen.Login.route,
                hasAccessToken = false
            )
        )
    }

    @Test
    fun protectedRoutesRestartAtSplashUntilRuntimeIsInitialized() {
        assertTrue(shouldRestartAtSplash(Screen.Home.route, runtimeReady = false))
        assertTrue(shouldRestartAtSplash(Screen.Rooms.route, runtimeReady = false))
        assertTrue(shouldRestartAtSplash(Screen.Room.route, runtimeReady = false))
        assertFalse(shouldRestartAtSplash(Screen.Room.route, runtimeReady = true))
        assertFalse(
            shouldRestartAtSplash(Screen.Bootstrap.route, runtimeReady = false)
        )
    }
}
