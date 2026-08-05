package mme.corp.audioshare.ui.screens.rooms

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateIsRendered() {
        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(),
                onAction = {},
                onBack = {}
            )
        }

        scrollToText("No active rooms")
        composeRule.onNodeWithText("No active rooms").assertExists()
    }

    @Test
    fun loadingStateDisablesControls() {
        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    runningOperation = RoomsUiOperation.LOAD_ROOMS
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule
            .onNodeWithTag(RoomsUiTestTags.LOADING)
            .assertExists()
        composeRule
            .onNodeWithTag(RoomsUiTestTags.REFRESH)
            .assertIsNotEnabled()
        scrollToTag(RoomsUiTestTags.CREATE_SUBMIT)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.CREATE_SUBMIT)
            .assertIsNotEnabled()
        scrollToTag(RoomsUiTestTags.JOIN_SUBMIT)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.JOIN_SUBMIT)
            .assertIsNotEnabled()
    }

    @Test
    fun lifecycleMutationDisablesBackNavigation() {
        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    session = RoomSessionState(
                        activeOperations = setOf(RoomSessionOperation.JOIN)
                    )
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithTag(RoomsUiTestTags.BACK).assertIsNotEnabled()
    }

    @Test
    fun currentRoomSectionShowsLifecycleSummary() {
        val current = room(
            id = "room-current",
            name = null,
            visibility = RoomVisibility.PRIVATE,
            role = RoomMemberRole.OWNER,
            memberCount = 3
        )

        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    session = RoomSessionState(
                        currentRoom = current
                    )
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Current room").assertExists()
        composeRule.onNodeWithText("Unnamed room").assertExists()
        composeRule.onNodeWithText("Visibility: Private").assertExists()
        composeRule.onNodeWithText("Role: OWNER").assertExists()
        composeRule.onNodeWithText("Active members: 3").assertExists()
        scrollToText("No other active rooms")
        composeRule.onNodeWithText("No other active rooms").assertExists()
    }

    @Test
    fun currentRoomIsNotRepeatedInActiveRoomsList() {
        val current = room("room-current", "Current team")

        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    session = RoomSessionState(
                        rooms = listOf(current),
                        currentRoom = current
                    )
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule
            .onAllNodesWithText("Current team")
            .assertCountEquals(1)
    }

    @Test
    fun activeRoomListRendersStableRoomData() {
        val first = room("room-1", "Alpha")
        val second = room("room-2", "Beta")

        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    session = RoomSessionState(rooms = listOf(first, second))
                ),
                onAction = {},
                onBack = {}
            )
        }

        scrollToText("Alpha")
        composeRule.onNodeWithText("Alpha").assertExists()
        scrollToText("Beta")
        composeRule.onNodeWithText("Beta").assertExists()
    }

    @Test
    fun archivedRoomsAreNotRenderedAsActive() {
        val archived = room("room-archived", "Archived room").copy(
            status = RoomStatus.ARCHIVED
        )

        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    session = RoomSessionState(rooms = listOf(archived))
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Archived room").assertDoesNotExist()
        scrollToText("No active rooms")
        composeRule.onNodeWithText("No active rooms").assertExists()
    }

    @Test
    fun errorStateRendersRetryAction() {
        val actions = mutableListOf<RoomsUiAction>()

        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    session = RoomSessionState(
                        lastError = RoomSessionError(
                            operation = RoomSessionOperation.LOAD_ROOMS,
                            type = IOException::class.java.simpleName,
                            retryable = true
                        )
                    ),
                    sessionErrorMessage = "Unable to load rooms."
                ),
                onAction = actions::add,
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Unable to load rooms.").assertExists()
        composeRule.onNodeWithText("Retry").performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(RoomsUiAction.LoadRooms))
        }
    }

    @Test
    fun nonRetryableLoadErrorDoesNotRenderRetryAction() {
        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    session = RoomSessionState(
                        lastError = RoomSessionError(
                            operation = RoomSessionOperation.LOAD_ROOMS,
                            type = "TestFailure",
                            retryable = false
                        )
                    ),
                    sessionErrorMessage = "Unable to load rooms."
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun retryableCreateErrorDoesNotRenderLoadRoomsRetryAction() {
        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    session = RoomSessionState(
                        lastError = RoomSessionError(
                            operation = RoomSessionOperation.CREATE,
                            type = "TestFailure",
                            retryable = true
                        )
                    ),
                    sessionErrorMessage = "Unable to create the room."
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun roomDetailsErrorIsNotRenderedOnRoomsList() {
        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(
                    session = RoomSessionState(
                        lastError = RoomSessionError(
                            operation = RoomSessionOperation.LEAVE,
                            roomId = "room-1",
                            type = IOException::class.java.simpleName,
                            retryable = true
                        )
                    ),
                    sessionErrorMessage = "Unable to leave the room."
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule
            .onNodeWithText("Unable to leave the room.")
            .assertDoesNotExist()
    }

    @Test
    fun createPrivateDispatchesFormAndSubmitActions() {
        val actions = mutableListOf<RoomsUiAction>()

        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(),
                onAction = actions::add,
                onBack = {}
            )
        }

        scrollToTag(RoomsUiTestTags.CREATE_NAME)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.CREATE_NAME)
            .performTextInput("Team room")
        scrollToTag(RoomsUiTestTags.CREATE_PRIVATE)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.CREATE_PRIVATE)
            .performClick()
        scrollToTag(RoomsUiTestTags.CREATE_SUBMIT)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.CREATE_SUBMIT)
            .performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(RoomsUiAction.RoomNameChanged("Team room")))
            assertTrue(
                actions.contains(
                    RoomsUiAction.CreateVisibilityChanged(RoomVisibility.PRIVATE)
                )
            )
            assertTrue(actions.contains(RoomsUiAction.CreateRoom))
        }
    }

    @Test
    fun createLocalDiscoveryDispatchesVisibilityAndSubmitActions() {
        val actions = mutableListOf<RoomsUiAction>()

        composeRule.setContent {
            RoomsScreenContent(
                state = RoomsUiState(),
                onAction = actions::add,
                onBack = {}
            )
        }

        scrollToTag(RoomsUiTestTags.CREATE_LOCAL)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.CREATE_LOCAL)
            .performClick()
        scrollToTag(RoomsUiTestTags.CREATE_SUBMIT)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.CREATE_SUBMIT)
            .performClick()

        composeRule.runOnIdle {
            assertTrue(
                actions.contains(
                    RoomsUiAction.CreateVisibilityChanged(
                        RoomVisibility.LOCAL_DISCOVERY
                    )
                )
            )
            assertTrue(actions.contains(RoomsUiAction.CreateRoom))
        }
    }

    @Test
    fun blankDebugJoinIsRejectedBeforeCoordinatorCall() {
        val coordinator = FakeRoomSessionCoordinator()
        composeRule.setContent {
            RoomsScreen(
                coordinator = coordinator,
                onBack = {},
                onNavigateRoom = {},
                onNavigateRooms = {}
            )
        }

        scrollToTag(RoomsUiTestTags.JOIN_SUBMIT)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.JOIN_SUBMIT)
            .performClick()

        composeRule.waitUntil {
            composeRule
                .onAllNodesWithText("Room ID is required.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.runOnIdle {
            assertEquals(0, coordinator.joinCalls)
        }
    }

    @Test
    fun successfulJoinNavigatesOnceAfterRecomposition() {
        val coordinator = FakeRoomSessionCoordinator()
        val navigationCalls = AtomicInteger(0)
        val recompositionTick = mutableIntStateOf(0)

        composeRule.setContent {
            key(recompositionTick.intValue) {
                RoomsScreen(
                    coordinator = coordinator,
                    onBack = {},
                    onNavigateRoom = { roomId ->
                        assertEquals("room-2", roomId)
                        navigationCalls.incrementAndGet()
                    },
                    onNavigateRooms = {}
                )
            }
        }

        scrollToTag(RoomsUiTestTags.JOIN_ID)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.JOIN_ID)
            .performTextInput("  room-2  ")
        composeRule
            .onNodeWithTag(RoomsUiTestTags.JOIN_SUBMIT)
            .performClick()

        composeRule.waitUntil {
            navigationCalls.get() == 1
        }

        composeRule.runOnIdle {
            recompositionTick.intValue += 1
        }
        composeRule.waitForIdle()

        assertEquals(1, navigationCalls.get())
        assertEquals(1, coordinator.joinCalls)
    }

    @Test
    fun failedJoinShowsFeedbackWithoutNavigation() {
        val coordinator = FakeRoomSessionCoordinator().apply {
            joinFailure = IOException("offline")
        }
        val navigationCalls = AtomicInteger(0)

        composeRule.setContent {
            RoomsScreen(
                coordinator = coordinator,
                onBack = {},
                onNavigateRoom = { navigationCalls.incrementAndGet() },
                onNavigateRooms = {}
            )
        }

        scrollToTag(RoomsUiTestTags.JOIN_ID)
        composeRule
            .onNodeWithTag(RoomsUiTestTags.JOIN_ID)
            .performTextInput("room-2")
        composeRule
            .onNodeWithTag(RoomsUiTestTags.JOIN_SUBMIT)
            .performClick()

        val expected =
            "Unable to reach ServeRelay. Check your connection and try again."
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(expected)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        assertEquals(0, navigationCalls.get())
        assertEquals(1, coordinator.joinCalls)
    }

    private fun scrollToTag(tag: String) {
        composeRule
            .onNodeWithTag(RoomsUiTestTags.LIST)
            .performScrollToNode(hasTestTag(tag))
    }

    private fun scrollToText(text: String) {
        composeRule
            .onNodeWithTag(RoomsUiTestTags.LIST)
            .performScrollToNode(hasText(text))
    }

    private class FakeRoomSessionCoordinator(
        initialState: RoomSessionState = RoomSessionState()
    ) : RoomSessionCoordinator {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<RoomSessionState> = mutableState

        var joinCalls = 0
        var joinFailure: Throwable? = null

        override suspend fun restoreFromBootstrap(
            bootstrap: SessionBootstrapResponse
        ): Result<RoomSessionState> = Result.success(state.value)

        override fun markDisconnected() = Unit

        override fun clearForLogout() {
            mutableState.value = RoomSessionState()
        }

        override fun resetAfterStartupFailure() {
            mutableState.value = RoomSessionState()
        }

        override suspend fun reconnect(): Result<RoomSessionState> =
            Result.success(state.value)

        override suspend fun loadRooms(): Result<List<Room>> =
            Result.success(state.value.rooms)

        override suspend fun openRoom(roomId: String): Result<RoomSessionState> =
            Result.success(state.value)

        override suspend fun activateRoom(
            roomId: String
        ): Result<RoomSessionState> = Result.success(state.value)

        override suspend fun deactivateCurrentRoom(): Result<RoomSessionState> =
            Result.success(state.value)

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
        ): Result<RoomSessionState> {
            joinCalls += 1
            joinFailure?.let { return Result.failure(it) }

            val joined = room(roomId, "Joined room")
            val next = RoomSessionState(
                rooms = listOf(joined),
                currentRoom = joined
            )
            mutableState.value = next
            return Result.success(next)
        }

        override suspend fun leaveCurrentRoom(): Result<RoomSessionState> =
            Result.success(state.value)

        override suspend fun archiveCurrentRoom(): Result<RoomSessionState> =
            Result.success(state.value)
    }

    private companion object {
        fun room(
            id: String,
            name: String? = "Room $id",
            visibility: RoomVisibility = RoomVisibility.LOCAL_DISCOVERY,
            role: RoomMemberRole = RoomMemberRole.MEMBER,
            memberCount: Long = 1
        ): Room = Room(
            id = id,
            ownerUserId = "owner-1",
            ownerDeviceId = "device-1",
            name = name,
            status = RoomStatus.ACTIVE,
            visibility = visibility,
            createdAt = "2026-08-01T10:00:00",
            updatedAt = "2026-08-01T10:00:00",
            archivedAt = null,
            currentUserRole = role,
            activeMemberCount = memberCount
        )
    }
}
