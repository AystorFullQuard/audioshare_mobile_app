package mme.corp.audioshare.ui.screens.rooms

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomMemberState
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.model.room.RoomMember
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
class RoomScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roomDetailsAndActiveMembersAreRenderedFromCoordinatorSnapshot() {
        val current = room(
            id = "room-1",
            name = null,
            role = RoomMemberRole.OWNER,
            memberCount = 4
        )
        val members = listOf(
            member(
                membershipId = "member-owner",
                displayName = "Ada",
                username = "ada",
                role = RoomMemberRole.OWNER,
                presence = PresenceState.IN_ROOM
            ),
            member(
                membershipId = "member-online",
                displayName = null,
                username = "bob",
                presence = PresenceState.ONLINE
            ),
            member(
                membershipId = "member-offline",
                displayName = "Cara",
                username = "cara",
                presence = PresenceState.OFFLINE,
                presenceLastSeenAt = "2026-08-01T12:00:00Z"
            ),
            member(
                membershipId = "member-private-last-seen",
                displayName = null,
                username = null,
                presence = PresenceState.OFFLINE,
                presenceLastSeenAt = null
            ),
            member(
                membershipId = "member-left",
                displayName = "Left member",
                username = "left",
                membershipState = RoomMemberState.LEFT,
                presence = PresenceState.OFFLINE
            )
        )

        composeRule.setContent {
            RoomScreenContent(
                roomId = current.id,
                state = RoomsUiState(
                    roomDetailsRoomId = current.id,
                    session = RoomSessionState(
                        currentRoom = current,
                        activeMembers = members
                    )
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Unnamed room").assertExists()
        composeRule.onNodeWithText("Visibility: Local discovery").assertExists()
        composeRule.onNodeWithText("Status: ACTIVE").assertExists()
        composeRule.onNodeWithText("Your role: OWNER").assertExists()
        composeRule.onNodeWithText("Active members: 4").assertExists()

        assertMemberContains("member-owner", "Ada")
        assertMemberContains("member-owner", "Presence: IN_ROOM")
        composeRule.onNodeWithContentDescription("Avatar for Ada").assertExists()

        assertMemberContains("member-online", "bob")
        assertMemberContains("member-online", "Presence: ONLINE")

        assertMemberContains("member-offline", "Cara")
        assertMemberContains("member-offline", "Presence: OFFLINE")
        assertMemberContains(
            "member-offline",
            "Last seen: 2026-08-01T12:00:00Z"
        )

        assertMemberContains("member-private-last-seen", "Unknown member")
        assertMemberContains("member-private-last-seen", "Last seen: Hidden")
        composeRule.onNodeWithText("Left member").assertDoesNotExist()
    }

    @Test
    fun routeInitializationShowsLoadingAndDisablesRefresh() {
        composeRule.setContent {
            RoomScreenContent(
                roomId = "room-1",
                state = RoomsUiState(),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithTag(RoomUiTestTags.LOADING).assertExists()
        composeRule.onNodeWithTag(RoomUiTestTags.REFRESH).assertIsNotEnabled()
        composeRule.onNodeWithText("Room is not available.").assertDoesNotExist()
    }

    @Test
    fun unrelatedRoomsListErrorIsNotRenderedOnRoomDetails() {
        val current = room(role = RoomMemberRole.MEMBER)
        composeRule.setContent {
            RoomScreenContent(
                roomId = current.id,
                state = RoomsUiState(
                    roomDetailsRoomId = current.id,
                    session = RoomSessionState(
                        currentRoom = current,
                        lastError = RoomSessionError(
                            operation = RoomSessionOperation.LOAD_ROOMS,
                            type = "TestFailure",
                            retryable = true
                        )
                    ),
                    sessionErrorMessage = "Unable to load rooms."
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Unable to load rooms.").assertDoesNotExist()
    }

    @Test
    fun unavailableRoomOffersExplicitRetry() {
        val actions = mutableListOf<RoomsUiAction>()
        composeRule.setContent {
            RoomScreenContent(
                roomId = "room-1",
                state = RoomsUiState(roomDetailsRoomId = "room-1"),
                onAction = actions::add,
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Room is not available.").assertExists()
        composeRule.onNodeWithText("Retry").performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(RoomsUiAction.RetryRoomDetails))
        }
    }

    @Test
    fun ownerSeesArchiveButNotLeaveAction() {
        setRoomContent(room(role = RoomMemberRole.OWNER))

        scrollToTag(RoomUiTestTags.ARCHIVE)
        composeRule.onNodeWithTag(RoomUiTestTags.ARCHIVE).assertExists()
        composeRule.onNodeWithTag(RoomUiTestTags.LEAVE).assertDoesNotExist()
    }

    @Test
    fun memberSeesLeaveButNotArchiveAction() {
        setRoomContent(room(role = RoomMemberRole.MEMBER))

        scrollToTag(RoomUiTestTags.LEAVE)
        composeRule.onNodeWithTag(RoomUiTestTags.LEAVE).assertExists()
        composeRule.onNodeWithTag(RoomUiTestTags.ARCHIVE).assertDoesNotExist()
    }

    @Test
    fun archiveConfirmationDispatchesRequestAndConfirmActions() {
        val actions = mutableListOf<RoomsUiAction>()
        val current = room(role = RoomMemberRole.OWNER)
        val state = mutableStateOf(
            RoomsUiState(
                roomDetailsRoomId = current.id,
                session = RoomSessionState(currentRoom = current)
            )
        )

        composeRule.setContent {
            RoomScreenContent(
                roomId = current.id,
                state = state.value,
                onAction = { action ->
                    actions += action
                    if (action == RoomsUiAction.RequestArchiveConfirmation) {
                        state.value = state.value.copy(
                            confirmation = RoomActionConfirmation.ARCHIVE
                        )
                    }
                },
                onBack = {}
            )
        }

        scrollToTag(RoomUiTestTags.ARCHIVE)
        composeRule.onNodeWithTag(RoomUiTestTags.ARCHIVE).performClick()
        composeRule.onNodeWithText("Archive room?").assertExists()
        composeRule.onNodeWithTag(RoomUiTestTags.CONFIRM).performClick()

        composeRule.runOnIdle {
            assertTrue(
                actions.contains(RoomsUiAction.RequestArchiveConfirmation)
            )
            assertTrue(actions.contains(RoomsUiAction.ConfirmRoomAction))
        }
    }

    @Test
    fun leaveConfirmationDispatchesRequestAndCancelActions() {
        val actions = mutableListOf<RoomsUiAction>()
        val current = room(role = RoomMemberRole.MEMBER)
        val state = mutableStateOf(
            RoomsUiState(
                roomDetailsRoomId = current.id,
                session = RoomSessionState(currentRoom = current)
            )
        )

        composeRule.setContent {
            RoomScreenContent(
                roomId = current.id,
                state = state.value,
                onAction = { action ->
                    actions += action
                    state.value = when (action) {
                        RoomsUiAction.RequestLeaveConfirmation ->
                            state.value.copy(
                                confirmation = RoomActionConfirmation.LEAVE
                            )
                        RoomsUiAction.DismissConfirmation ->
                            state.value.copy(confirmation = null)
                        else -> state.value
                    }
                },
                onBack = {}
            )
        }

        scrollToTag(RoomUiTestTags.LEAVE)
        composeRule.onNodeWithTag(RoomUiTestTags.LEAVE).performClick()
        composeRule.onNodeWithText("Leave room?").assertExists()
        composeRule.onNodeWithTag(RoomUiTestTags.CANCEL).performClick()

        composeRule.runOnIdle {
            assertTrue(
                actions.contains(RoomsUiAction.RequestLeaveConfirmation)
            )
            assertTrue(actions.contains(RoomsUiAction.DismissConfirmation))
        }
    }

    @Test
    fun destructiveConfirmationIsDisabledWhileBusy() {
        val current = room(role = RoomMemberRole.OWNER)
        composeRule.setContent {
            RoomScreenContent(
                roomId = current.id,
                state = RoomsUiState(
                    roomDetailsRoomId = current.id,
                    confirmation = RoomActionConfirmation.ARCHIVE,
                    runningOperation = RoomsUiOperation.ARCHIVE_ROOM,
                    session = RoomSessionState(currentRoom = current)
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithTag(RoomUiTestTags.LOADING).assertExists()
        composeRule.onNodeWithTag(RoomUiTestTags.BACK).assertIsNotEnabled()
        composeRule.onNodeWithTag(RoomUiTestTags.CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(RoomUiTestTags.CANCEL).assertIsNotEnabled()
    }

    @Test
    fun coordinatorLifecycleMutationDisablesBack() {
        val current = room(role = RoomMemberRole.MEMBER)
        composeRule.setContent {
            RoomScreenContent(
                roomId = current.id,
                state = RoomsUiState(
                    roomDetailsRoomId = current.id,
                    session = RoomSessionState(
                        currentRoom = current,
                        activeOperations = setOf(RoomSessionOperation.OPEN_ROOM)
                    )
                ),
                onAction = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithTag(RoomUiTestTags.BACK).assertIsNotEnabled()
    }

    @Test
    fun toolbarBackDeactivatesAndNavigatesOnceKeepingMembership() {
        val current = room(role = RoomMemberRole.MEMBER)
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        )
        val navigationCalls = AtomicInteger(0)

        composeRule.setContent {
            RoomScreen(
                roomId = current.id,
                coordinator = coordinator,
                onNavigateRooms = { navigationCalls.incrementAndGet() }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RoomUiTestTags.BACK).performClick()
        composeRule.waitUntil { navigationCalls.get() == 1 }

        assertEquals(1, coordinator.deactivateCalls)
        assertEquals(listOf(current.id), coordinator.state.value.rooms.map { it.id })
        assertEquals(null, coordinator.state.value.currentRoom)
        composeRule.onNodeWithText("Room is not available.").assertDoesNotExist()
        composeRule.onNodeWithTag(RoomUiTestTags.LOADING).assertExists()

        composeRule.runOnIdle {
            coordinator.emit(coordinator.state.value.copy())
        }
        composeRule.waitForIdle()
        assertEquals(1, navigationCalls.get())
    }

    @Test
    fun systemBackUsesSameDeactivateFlow() {
        val current = room(role = RoomMemberRole.MEMBER)
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        )
        val navigationCalls = AtomicInteger(0)

        composeRule.setContent {
            RoomScreen(
                roomId = current.id,
                coordinator = coordinator,
                onNavigateRooms = { navigationCalls.incrementAndGet() }
            )
        }
        composeRule.waitForIdle()

        pressBack()
        composeRule.waitUntil { navigationCalls.get() == 1 }

        assertEquals(1, coordinator.deactivateCalls)
        assertEquals(null, coordinator.state.value.currentRoom)
        assertEquals(listOf(current.id), coordinator.state.value.rooms.map { it.id })
    }

    @Test
    fun failedDeactivateStaysOnRoomAndRetryUsesDeactivateAgain() {
        val current = room(role = RoomMemberRole.MEMBER)
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        ).apply {
            deactivateFailuresRemaining = 1
        }
        val navigationCalls = AtomicInteger(0)

        composeRule.setContent {
            RoomScreen(
                roomId = current.id,
                coordinator = coordinator,
                onNavigateRooms = { navigationCalls.incrementAndGet() }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RoomUiTestTags.BACK).performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Retry")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        assertEquals(0, navigationCalls.get())
        assertEquals(1, coordinator.deactivateCalls)
        assertEquals(current.id, coordinator.state.value.currentRoom?.id)

        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitUntil { navigationCalls.get() == 1 }

        assertEquals(2, coordinator.deactivateCalls)
        assertEquals(null, coordinator.state.value.currentRoom)
    }

    @Test
    fun inFlightDeactivateConsumesDuplicateSystemBack() {
        val current = room(role = RoomMemberRole.MEMBER)
        val gate = CompletableDeferred<Unit>()
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        ).apply {
            deactivateGate = gate
        }
        val navigationCalls = AtomicInteger(0)

        composeRule.setContent {
            RoomScreen(
                roomId = current.id,
                coordinator = coordinator,
                onNavigateRooms = { navigationCalls.incrementAndGet() }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RoomUiTestTags.BACK).performClick()
        composeRule.waitUntil { coordinator.deactivateCalls == 1 }
        composeRule.onNodeWithTag(RoomUiTestTags.BACK).assertIsNotEnabled()

        pressBack()
        composeRule.waitForIdle()
        assertEquals(1, coordinator.deactivateCalls)
        assertEquals(0, navigationCalls.get())

        composeRule.runOnIdle { gate.complete(Unit) }
        composeRule.waitUntil { navigationCalls.get() == 1 }
        assertEquals(1, coordinator.deactivateCalls)
    }

    @Test
    fun ownerArchiveSuccessNavigatesBackOnce() {
        val current = room(role = RoomMemberRole.OWNER)
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = current)
        )
        val navigationCalls = AtomicInteger(0)

        composeRule.setContent {
            RoomScreen(
                roomId = current.id,
                coordinator = coordinator,
                onNavigateRooms = { navigationCalls.incrementAndGet() }
            )
        }
        composeRule.waitForIdle()
        assertEquals(0, coordinator.openCalls)

        scrollToTag(RoomUiTestTags.ARCHIVE)
        composeRule.onNodeWithTag(RoomUiTestTags.ARCHIVE).performClick()
        composeRule.onNodeWithTag(RoomUiTestTags.CONFIRM).performClick()
        composeRule.waitUntil { navigationCalls.get() == 1 }

        composeRule.runOnIdle {
            coordinator.emit(RoomSessionState())
        }
        composeRule.waitForIdle()

        assertEquals(1, navigationCalls.get())
        assertEquals(1, coordinator.archiveCalls)
    }

    @Test
    fun memberLeaveFailureShowsFeedbackWithoutNavigation() {
        val current = room(role = RoomMemberRole.MEMBER)
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = current)
        ).apply {
            leaveFailure = IOException("offline")
        }
        val navigationCalls = AtomicInteger(0)

        composeRule.setContent {
            RoomScreen(
                roomId = current.id,
                coordinator = coordinator,
                onNavigateRooms = { navigationCalls.incrementAndGet() }
            )
        }
        composeRule.waitForIdle()
        assertEquals(0, coordinator.openCalls)

        scrollToTag(RoomUiTestTags.LEAVE)
        composeRule.onNodeWithTag(RoomUiTestTags.LEAVE).performClick()
        composeRule.onNodeWithTag(RoomUiTestTags.CONFIRM).performClick()

        val expected =
            "Unable to reach ServeRelay. Check your connection and try again."
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(expected)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        assertEquals(0, navigationCalls.get())
        assertEquals(1, coordinator.leaveCalls)
    }

    @Test
    fun externalRoomDisappearanceNavigatesBackOnce() {
        val current = room(role = RoomMemberRole.MEMBER)
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = current)
        )
        val navigationCalls = AtomicInteger(0)

        composeRule.setContent {
            RoomScreen(
                roomId = current.id,
                coordinator = coordinator,
                onNavigateRooms = { navigationCalls.incrementAndGet() }
            )
        }
        composeRule.waitForIdle()
        assertEquals(0, coordinator.openCalls)

        composeRule.runOnIdle {
            coordinator.emit(RoomSessionState())
        }
        composeRule.waitUntil { navigationCalls.get() == 1 }

        composeRule.runOnIdle {
            coordinator.emit(RoomSessionState())
        }
        composeRule.waitForIdle()

        assertEquals(1, navigationCalls.get())
    }

    @Test
    fun busyCoordinatorDefersInitializationWithoutReloadingCurrentRoom() {
        val current = room(role = RoomMemberRole.MEMBER)
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                currentRoom = current,
                activeOperations = setOf(RoomSessionOperation.LOAD_ROOMS)
            )
        )

        composeRule.setContent {
            RoomScreen(
                roomId = current.id,
                coordinator = coordinator,
                onNavigateRooms = {}
            )
        }
        composeRule.waitForIdle()
        assertEquals(0, coordinator.openCalls)

        composeRule.runOnIdle {
            coordinator.emit(
                RoomSessionState(currentRoom = current)
            )
        }
        composeRule.waitForIdle()
        scrollToTag(RoomUiTestTags.LEAVE)

        assertEquals(0, coordinator.openCalls)
    }

    @Test
    fun headerActionsExposeAccessibilityDescriptions() {
        setRoomContent(room(role = RoomMemberRole.MEMBER))

        composeRule.onNodeWithContentDescription("Back to rooms").assertExists()
        composeRule
            .onNodeWithContentDescription("Refresh room details")
            .assertExists()
    }

    private fun setRoomContent(current: Room) {
        composeRule.setContent {
            RoomScreenContent(
                roomId = current.id,
                state = RoomsUiState(
                    roomDetailsRoomId = current.id,
                    session = RoomSessionState(currentRoom = current)
                ),
                onAction = {},
                onBack = {}
            )
        }
    }

    private fun assertMemberContains(
        membershipId: String,
        text: String
    ) {
        val tag = RoomUiTestTags.member(membershipId)
        scrollToTag(tag)
        composeRule.onNode(
            hasTestTag(tag) and hasAnyDescendant(hasText(text))
        ).assertExists()
    }

    private fun scrollToTag(tag: String) {
        composeRule
            .onNodeWithTag(RoomUiTestTags.LIST)
            .performScrollToNode(hasTestTag(tag))
    }

    private class FakeRoomSessionCoordinator(
        initialState: RoomSessionState
    ) : RoomSessionCoordinator {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<RoomSessionState> = mutableState

        var openCalls = 0
        var deactivateCalls = 0
        var leaveCalls = 0
        var archiveCalls = 0
        var deactivateFailuresRemaining = 0
        var deactivateGate: CompletableDeferred<Unit>? = null
        var leaveFailure: Throwable? = null
        var archiveFailure: Throwable? = null

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
            return Result.success(state.value)
        }

        override suspend fun activateRoom(
            roomId: String
        ): Result<RoomSessionState> = Result.success(state.value)

        override suspend fun deactivateCurrentRoom(): Result<RoomSessionState> {
            deactivateCalls += 1
            deactivateGate?.await()

            if (deactivateFailuresRemaining > 0) {
                deactivateFailuresRemaining -= 1
                val roomId = state.value.currentRoom?.id
                val exception = IOException("offline")
                emit(
                    state.value.copy(
                        lastError = RoomSessionError(
                            operation = RoomSessionOperation.DEACTIVATE,
                            roomId = roomId,
                            type = IOException::class.java.simpleName,
                            retryable = true
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
        ): Result<RoomSessionState> = Result.success(state.value)

        override suspend fun leaveCurrentRoom(): Result<RoomSessionState> {
            leaveCalls += 1
            leaveFailure?.let { return Result.failure(it) }
            val next = RoomSessionState()
            emit(next)
            return Result.success(next)
        }

        override suspend fun archiveCurrentRoom(): Result<RoomSessionState> {
            archiveCalls += 1
            archiveFailure?.let { return Result.failure(it) }
            val next = RoomSessionState()
            emit(next)
            return Result.success(next)
        }
    }

    private companion object {
        fun room(
            id: String = "room-1",
            name: String? = "Team room",
            role: RoomMemberRole,
            memberCount: Long = 1
        ): Room = Room(
            id = id,
            ownerUserId = "owner-1",
            ownerDeviceId = "device-1",
            name = name,
            status = RoomStatus.ACTIVE,
            visibility = RoomVisibility.LOCAL_DISCOVERY,
            createdAt = "2026-08-01T10:00:00Z",
            updatedAt = "2026-08-01T10:00:00Z",
            archivedAt = null,
            currentUserRole = role,
            activeMemberCount = memberCount
        )

        fun member(
            membershipId: String,
            displayName: String?,
            username: String?,
            role: RoomMemberRole = RoomMemberRole.MEMBER,
            membershipState: RoomMemberState = RoomMemberState.ACTIVE,
            presence: PresenceState?,
            presenceLastSeenAt: String? = null
        ): RoomMember = RoomMember(
            membershipId = membershipId,
            roomId = "room-1",
            userId = "user-$membershipId",
            username = username,
            displayName = displayName,
            avatarURL = null,
            role = role,
            membershipState = membershipState,
            presenceState = presence,
            presenceLastSeenAt = presenceLastSeenAt,
            joinedAt = "2026-08-01T10:00:00Z",
            membershipLastSeenAt = "2026-08-01T12:00:00Z",
            leftAt = if (membershipState == RoomMemberState.LEFT) {
                "2026-08-01T12:30:00Z"
            } else {
                null
            }
        )
    }
}
