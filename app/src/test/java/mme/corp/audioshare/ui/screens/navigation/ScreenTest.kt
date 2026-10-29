package mme.corp.audioshare.ui.screens.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {
    @Test
    fun homeIsDefaultAndLoginIsExplicit() {
        assertEquals("home", Screen.Home.route)
        assertEquals("login", Screen.Login.route)
        assertFalse(shouldReturnHome(Screen.Home.route, runtimeReady = false))
        assertFalse(shouldReturnHome(Screen.Login.route, runtimeReady = false))
    }

    @Test
    fun protectedRoomsRequireBootstrappedSession() {
        assertTrue(shouldReturnHome(Screen.Rooms.route, runtimeReady = false))
        assertTrue(shouldReturnHome(Screen.Room.route, runtimeReady = false))
        assertFalse(shouldReturnHome(Screen.Room.route, runtimeReady = true))
    }

    @Test
    fun roomRouteUsesOnlyRoomIdArgument() {
        assertEquals("room/{roomId}", Screen.Room.route)
        assertEquals("room/room-123", Screen.Room.createRoute("  room-123  "))
    }
}
