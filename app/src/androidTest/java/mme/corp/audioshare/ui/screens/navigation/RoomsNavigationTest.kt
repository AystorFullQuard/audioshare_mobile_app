package mme.corp.audioshare.ui.screens.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.room.RoomSessionCoordinator
import mme.corp.audioshare.room.RoomSessionError
import mme.corp.audioshare.room.RoomSessionOperation
import mme.corp.audioshare.room.RoomSessionState
import mme.corp.audioshare.ui.screens.rooms.RoomUiTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RoomsNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listOpenUsesOneCoordinatorRequestAndRestoresRoomsBackStack() {
        val current = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        )
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            val context = LocalContext.current
            navController = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Rooms.route
            ) {
                roomsDestinations(navController, coordinator)
            }
        }

        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitUntil {
            navController.currentDestination?.route == Screen.Room.route
        }

        composeRule.runOnIdle {
            assertEquals(1, coordinator.openCalls)
            assertEquals(
                current.id,
                navController.currentBackStackEntry
                    ?.arguments
                    ?.getString(Screen.ROOM_ID_ARGUMENT)
            )
            coordinator.emit(RoomSessionState())
        }

        composeRule.waitUntil {
            navController.currentDestination?.route == Screen.Rooms.route
        }
        composeRule.runOnIdle {
            assertFalse(navController.popBackStack())
            assertEquals(1, coordinator.openCalls)
        }
    }

    @Test
    fun roomBackDeactivatesBeforeReturningToRooms() {
        val current = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        )
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            val context = LocalContext.current
            navController = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Rooms.route
            ) {
                roomsDestinations(navController, coordinator)
            }
        }

        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitUntil {
            navController.currentDestination?.route == Screen.Room.route
        }

        composeRule.onNodeWithTag(RoomUiTestTags.BACK).performClick()
        composeRule.waitUntil {
            navController.currentDestination?.route == Screen.Rooms.route
        }

        composeRule.runOnIdle {
            assertEquals(1, coordinator.deactivateCalls)
            assertEquals(null, coordinator.state.value.currentRoom)
            assertEquals(
                listOf(current.id),
                coordinator.state.value.rooms.map { it.id }
            )
        }
    }

    @Test
    fun failedRoomBackKeepsRoomDestination() {
        val current = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        ).apply {
            deactivateFailure = IOException("offline")
        }
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            val context = LocalContext.current
            navController = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Rooms.route
            ) {
                roomsDestinations(navController, coordinator)
            }
        }

        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitUntil {
            navController.currentDestination?.route == Screen.Room.route
        }

        composeRule.onNodeWithTag(RoomUiTestTags.BACK).performClick()
        composeRule.waitUntil { coordinator.deactivateCalls == 1 }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(Screen.Room.route, navController.currentDestination?.route)
            assertEquals(current.id, coordinator.state.value.currentRoom?.id)
        }
    }

    @Test
    fun startupGateDoesNotCreateProtectedRoomsContent() {
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = room("room-1"))
        )
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            val context = LocalContext.current
            navController = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Rooms.route
            ) {
                roomsDestinations(
                    navController = navController,
                    coordinator = coordinator,
                    isStartupRuntimeReady = { false }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rooms").assertDoesNotExist()
        assertEquals(0, coordinator.openCalls)
    }

    @Test
    fun directRoomRouteOpensExactlyOnce() {
        val coordinator = FakeRoomSessionCoordinator()
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            val context = LocalContext.current
            navController = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Rooms.route
            ) {
                roomsDestinations(navController, coordinator)
            }
        }

        composeRule.runOnIdle {
            navController.navigate(Screen.Room.createRoute("room-direct"))
        }
        composeRule.waitUntil { coordinator.openCalls == 1 }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(1, coordinator.openCalls)
            assertEquals("room-direct", coordinator.state.value.currentRoom?.id)
        }
    }

    private class FakeRoomSessionCoordinator(
        initialState: RoomSessionState = RoomSessionState()
    ) : RoomSessionCoordinator {

        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<RoomSessionState> = mutableState

        var openCalls: Int = 0
            private set
        var deactivateCalls: Int = 0
            private set
        var deactivateFailure: Throwable? = null

        fun emit(state: RoomSessionState) {
            mutableState.value = state
        }

        override suspend fun restoreFromBootstrap(
            bootstrap: SessionBootstrapResponse
        ): Result<RoomSessionState> = Result.success(state.value)

        override fun markDisconnected() = Unit

        override fun clearForLogout() {
            emit(RoomSessionState())
        }

        override fun resetAfterStartupFailure() {
            emit(RoomSessionState())
        }

        override suspend fun reconnect(): Result<RoomSessionState> =
            Result.success(state.value)

        override suspend fun loadRooms(): Result<List<Room>> =
            Result.success(state.value.rooms)

        override suspend fun openRoom(roomId: String): Result<RoomSessionState> {
            openCalls += 1
            val selected = state.value.currentRoom
                ?.takeIf { current -> current.id == roomId }
                ?: room(roomId)
            val next = state.value.copy(
                rooms = state.value.rooms
                    .filterNot { room -> room.id == roomId } + selected,
                currentRoom = selected
            )
            emit(next)
            return Result.success(next)
        }

        override suspend fun activateRoom(
            roomId: String
        ): Result<RoomSessionState> = Result.success(state.value)

        override suspend fun deactivateCurrentRoom(): Result<RoomSessionState> {
            deactivateCalls += 1
            deactivateFailure?.let { exception ->
                val roomId = state.value.currentRoom?.id
                emit(
                    state.value.copy(
                        lastError = RoomSessionError(
                            operation = RoomSessionOperation.DEACTIVATE,
                            roomId = roomId,
                            type = exception::class.java.simpleName,
                            retryable = exception is IOException
                        )
                    )
                )
                return Result.failure(exception)
            }

            val next = state.value.copy(
                currentRoom = null,
                activeMembers = emptyList(),
                lastError = null
            )
            emit(next)
            return Result.success(next)
        }

        override suspend fun refreshCurrentRoom(): Result<RoomSessionState> =
            Result.success(state.value)

        override suspend fun refreshActiveMembers(): Result<RoomSessionState> =
            Result.success(state.value)

        override suspend fun createRoom(
            name: String?,
            visibility: RoomVisibility
        ): Result<RoomSessionState> = Result.success(state.value)

        override suspend fun joinLocalDiscoveryRoom(
            roomId: String
        ): Result<RoomSessionState> = openRoom(roomId)

        override suspend fun leaveCurrentRoom(): Result<RoomSessionState> =
            Result.success(state.value)

        override suspend fun archiveCurrentRoom(): Result<RoomSessionState> =
            Result.success(state.value)
    }

    private companion object {
        fun room(id: String): Room = Room(
            id = id,
            ownerUserId = "owner-1",
            ownerDeviceId = "device-1",
            name = "Room $id",
            status = RoomStatus.ACTIVE,
            visibility = RoomVisibility.LOCAL_DISCOVERY,
            createdAt = "2026-08-01T10:00:00Z",
            updatedAt = "2026-08-01T10:00:00Z",
            archivedAt = null,
            currentUserRole = RoomMemberRole.MEMBER,
            activeMemberCount = 1
        )
    }
}
