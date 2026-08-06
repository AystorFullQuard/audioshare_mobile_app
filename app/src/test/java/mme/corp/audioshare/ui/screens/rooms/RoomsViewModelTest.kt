package mme.corp.audioshare.ui.screens.rooms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.exception.ApiErrorResponse
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.room.RoomSessionCoordinator
import mme.corp.audioshare.room.RoomSessionError
import mme.corp.audioshare.room.RoomSessionOperation
import mme.corp.audioshare.room.RoomSessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RoomsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateUsesCoordinatorSnapshot() = runTest(dispatcher) {
        val current = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        )

        val viewModel = RoomsViewModel(coordinator)

        assertEquals(listOf(current), viewModel.uiState.value.rooms)
        assertEquals(current, viewModel.uiState.value.currentRoom)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun activationOperationsUseSafeFallbackMessages() = runTest(dispatcher) {
        val expectations = listOf(
            RoomSessionOperation.ACTIVATE to "Unable to open the room.",
            RoomSessionOperation.DEACTIVATE to "Unable to close the room."
        )

        expectations.forEach { (operation, expectedMessage) ->
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(
                    lastError = RoomSessionError(
                        operation = operation,
                        type = "IllegalStateException"
                    )
                )
            )

            val viewModel = RoomsViewModel(coordinator)

            assertEquals(
                expectedMessage,
                viewModel.uiState.value.sessionErrorMessage
            )
        }
    }

    @Test
    fun coordinatorUpdatesSessionWithoutClearingFormState() = runTest(dispatcher) {
        val coordinator = FakeRoomSessionCoordinator()
        val viewModel = RoomsViewModel(coordinator)
        viewModel.onAction(RoomsUiAction.RoomNameChanged("Team room"))
        viewModel.onAction(RoomsUiAction.JoinRoomIdChanged("room-2"))

        coordinator.emit(
            RoomSessionState(rooms = listOf(room("room-1")))
        )
        advanceUntilIdle()

        assertEquals("Team room", viewModel.uiState.value.roomNameInput)
        assertEquals("room-2", viewModel.uiState.value.joinRoomIdInput)
        assertEquals(listOf("room-1"), viewModel.uiState.value.rooms.map(Room::id))
    }

    @Test
    fun formActionsUpdateTransientState() = runTest(dispatcher) {
        val viewModel = RoomsViewModel(FakeRoomSessionCoordinator())

        viewModel.onAction(RoomsUiAction.RoomNameChanged("Private room"))
        viewModel.onAction(
            RoomsUiAction.CreateVisibilityChanged(
                RoomVisibility.LOCAL_DISCOVERY
            )
        )
        viewModel.onAction(RoomsUiAction.JoinRoomIdChanged("room-9"))

        assertEquals("Private room", viewModel.uiState.value.roomNameInput)
        assertEquals(
            RoomVisibility.LOCAL_DISCOVERY,
            viewModel.uiState.value.createVisibility
        )
        assertEquals("room-9", viewModel.uiState.value.joinRoomIdInput)
    }

    @Test
    fun transientInputsAreBoundedBeforeSavedStatePersistence() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val viewModel = RoomsViewModel(FakeRoomSessionCoordinator(), handle)

            viewModel.onAction(RoomsUiAction.RoomNameChanged("n".repeat(100)))
            viewModel.onAction(RoomsUiAction.JoinRoomIdChanged("i".repeat(200)))

            assertEquals(80, viewModel.uiState.value.roomNameInput.length)
            assertEquals(128, viewModel.uiState.value.joinRoomIdInput.length)

            val recreated = RoomsViewModel(FakeRoomSessionCoordinator(), handle)
            assertEquals(80, recreated.uiState.value.roomNameInput.length)
            assertEquals(128, recreated.uiState.value.joinRoomIdInput.length)
        }

    @Test
    fun transitionGuardsIncludeLocalAndCoordinatorLifecycleMutations() {
        assertTrue(
            RoomsUiState(
                runningOperation = RoomsUiOperation.CREATE_ROOM
            ).isRoomTransitionRunning
        )
        assertTrue(
            RoomsUiState(
                runningOperation = RoomsUiOperation.DEACTIVATE_ROOM
            ).isRoomTransitionRunning
        )
        assertTrue(RoomsUiState(roomExitPending = true).isBusy)
        assertTrue(
            RoomsUiState(roomExitPending = true).isRoomTransitionRunning
        )
        assertTrue(
            RoomsUiState(
                session = RoomSessionState(
                    activeOperations = setOf(RoomSessionOperation.JOIN)
                )
            ).isRoomTransitionRunning
        )
        assertTrue(
            RoomsUiState(
                session = RoomSessionState(
                    activeOperations = setOf(RoomSessionOperation.ACTIVATE)
                )
            ).isRoomTransitionRunning
        )
        assertTrue(
            RoomsUiState(
                session = RoomSessionState(
                    activeOperations = setOf(RoomSessionOperation.DEACTIVATE)
                )
            ).isRoomTransitionRunning
        )
        assertTrue(
            RoomsUiState(
                session = RoomSessionState(
                    activeOperations = setOf(RoomSessionOperation.ARCHIVE)
                )
            ).isDestructiveOperationRunning
        )
        assertFalse(
            RoomsUiState(
                session = RoomSessionState(
                    activeOperations = setOf(RoomSessionOperation.LOAD_ROOMS)
                )
            ).isRoomTransitionRunning
        )
    }

    @Test
    fun createRoomKeepsListContextClearsNameAndShowsFeedback() =
        runTest(dispatcher) {
            val current = room("room-current")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(
                    rooms = listOf(current),
                    currentRoom = current
                )
            )
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }
            viewModel.onAction(RoomsUiAction.RoomNameChanged("  Team room  "))
            viewModel.onAction(
                RoomsUiAction.CreateVisibilityChanged(
                    RoomVisibility.LOCAL_DISCOVERY
                )
            )

            viewModel.onAction(RoomsUiAction.CreateRoom)
            advanceUntilIdle()

            assertEquals("Team room", coordinator.createdName)
            assertEquals(
                RoomVisibility.LOCAL_DISCOVERY,
                coordinator.createdVisibility
            )
            assertTrue(observedEvents.isEmpty())
            assertEquals("room-current", viewModel.uiState.value.currentRoom?.id)
            assertEquals(
                listOf("room-current", "created-room"),
                viewModel.uiState.value.rooms.map { it.id }
            )
            assertEquals("", viewModel.uiState.value.roomNameInput)
            assertEquals(
                "Room created. Use Open to enter it.",
                viewModel.uiState.value.feedback?.message
            )
            assertFalse(viewModel.uiState.value.feedback?.isError == true)
            collector.cancel()
        }

    @Test
    fun blankCreateNameIsSentAsNull() = runTest(dispatcher) {
        val coordinator = FakeRoomSessionCoordinator()
        val viewModel = RoomsViewModel(coordinator)
        viewModel.onAction(RoomsUiAction.RoomNameChanged("   "))

        viewModel.onAction(RoomsUiAction.CreateRoom)
        advanceUntilIdle()

        assertNull(coordinator.createdName)
        assertNull(viewModel.uiState.value.currentRoom)
        assertEquals(listOf("created-room"), viewModel.uiState.value.rooms.map { it.id })
        assertEquals(
            "Room created. Use Open to enter it.",
            viewModel.uiState.value.feedback?.message
        )
    }

    @Test
    fun createDoesNotReportSuccessWhenMembershipSnapshotIsMissing() =
        runTest(dispatcher) {
            val coordinator = FakeRoomSessionCoordinator().apply {
                createResult = Result.success(RoomSessionState())
            }
            val viewModel = RoomsViewModel(coordinator)
            viewModel.onAction(RoomsUiAction.RoomNameChanged("Team room"))

            viewModel.onAction(RoomsUiAction.CreateRoom)
            advanceUntilIdle()

            assertEquals(
                "Room creation returned an inconsistent state.",
                viewModel.uiState.value.feedback?.message
            )
            assertTrue(viewModel.uiState.value.feedback?.isError == true)
            assertEquals("Team room", viewModel.uiState.value.roomNameInput)
        }

    @Test
    fun createDoesNotReportSuccessWhenCoordinatorActivatesCreatedRoom() =
        runTest(dispatcher) {
            val created = room("created-room").copy(
                currentUserRole = RoomMemberRole.OWNER
            )
            val coordinator = FakeRoomSessionCoordinator().apply {
                createResult = Result.success(
                    RoomSessionState(
                        rooms = listOf(created),
                        currentRoom = created
                    )
                )
            }
            val viewModel = RoomsViewModel(coordinator)
            viewModel.onAction(RoomsUiAction.RoomNameChanged("Team room"))

            viewModel.onAction(RoomsUiAction.CreateRoom)
            advanceUntilIdle()

            assertEquals(
                "Room creation returned an inconsistent state.",
                viewModel.uiState.value.feedback?.message
            )
            assertTrue(viewModel.uiState.value.feedback?.isError == true)
            assertEquals("Team room", viewModel.uiState.value.roomNameInput)
        }

    @Test
    fun blankJoinIsRejectedBeforeCoordinatorCall() = runTest(dispatcher) {
        val coordinator = FakeRoomSessionCoordinator()
        val viewModel = RoomsViewModel(coordinator)
        viewModel.onAction(RoomsUiAction.JoinRoomIdChanged("   "))

        viewModel.onAction(RoomsUiAction.JoinLocalDiscoveryRoom)
        advanceUntilIdle()

        assertEquals(0, coordinator.joinCalls)
        assertEquals(
            "Room ID is required.",
            viewModel.uiState.value.feedback?.message
        )
        assertTrue(viewModel.uiState.value.feedback?.isError == true)
    }

    @Test
    fun joinKeepsListContextClearsInputAndShowsFeedback() =
        runTest(dispatcher) {
            val current = room("room-current")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(
                    rooms = listOf(current),
                    currentRoom = current
                )
            )
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }
            viewModel.onAction(RoomsUiAction.JoinRoomIdChanged("  room-2  "))

            viewModel.onAction(RoomsUiAction.JoinLocalDiscoveryRoom)
            advanceUntilIdle()

            assertEquals("room-2", coordinator.joinedRoomId)
            assertTrue(observedEvents.isEmpty())
            assertEquals("room-current", viewModel.uiState.value.currentRoom?.id)
            assertEquals(
                listOf("room-current", "room-2"),
                viewModel.uiState.value.rooms.map { it.id }
            )
            assertEquals("", viewModel.uiState.value.joinRoomIdInput)
            assertEquals(
                "Room joined. Use Open to enter it.",
                viewModel.uiState.value.feedback?.message
            )
            assertFalse(viewModel.uiState.value.feedback?.isError == true)
            collector.cancel()
        }

    @Test
    fun joinDoesNotReportSuccessWhenRequestedMembershipIsMissing() =
        runTest(dispatcher) {
            val coordinator = FakeRoomSessionCoordinator().apply {
                joinResult = Result.success(
                    RoomSessionState(rooms = listOf(room("room-other")))
                )
            }
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }
            viewModel.onAction(RoomsUiAction.JoinRoomIdChanged("room-requested"))

            viewModel.onAction(RoomsUiAction.JoinLocalDiscoveryRoom)
            advanceUntilIdle()

            assertTrue(observedEvents.isEmpty())
            assertEquals(
                "Room join returned an inconsistent state.",
                viewModel.uiState.value.feedback?.message
            )
            assertEquals("room-requested", viewModel.uiState.value.joinRoomIdInput)
            collector.cancel()
        }

    @Test
    fun joinDoesNotReportSuccessWhenCoordinatorActivatesJoinedRoom() =
        runTest(dispatcher) {
            val joined = room("room-requested")
            val coordinator = FakeRoomSessionCoordinator().apply {
                joinResult = Result.success(
                    RoomSessionState(
                        rooms = listOf(joined),
                        currentRoom = joined
                    )
                )
            }
            val viewModel = RoomsViewModel(coordinator)
            viewModel.onAction(RoomsUiAction.JoinRoomIdChanged("room-requested"))

            viewModel.onAction(RoomsUiAction.JoinLocalDiscoveryRoom)
            advanceUntilIdle()

            assertEquals(
                "Room join returned an inconsistent state.",
                viewModel.uiState.value.feedback?.message
            )
            assertTrue(viewModel.uiState.value.feedback?.isError == true)
            assertEquals("room-requested", viewModel.uiState.value.joinRoomIdInput)
        }

    @Test
    fun openRoomNavigatesOnlyAfterCoordinatorSelectsRequestedRoom() =
        runTest(dispatcher) {
            val coordinator = FakeRoomSessionCoordinator()
            val viewModel = RoomsViewModel(coordinator)
            val event = async { viewModel.events.first() }

            viewModel.onAction(RoomsUiAction.OpenRoom("  room-3  "))
            advanceUntilIdle()

            assertEquals("room-3", coordinator.openedRoomId)
            assertEquals(
                RoomsUiEvent.NavigateToRoom("room-3"),
                event.await()
            )
        }

    @Test
    fun unavailableRoomUsesSafeErrorAndNavigatesBackToRooms() =
        runTest(dispatcher) {
            val unavailable = RoomSessionState(
                lastError = RoomSessionError(
                    operation = RoomSessionOperation.OPEN_ROOM,
                    roomId = "room-3",
                    type = "ApiException",
                    httpCode = 409,
                    apiCode = "ROOM_ARCHIVED"
                )
            )
            val coordinator = FakeRoomSessionCoordinator().apply {
                openRoomResult = Result.success(unavailable)
            }
            val viewModel = RoomsViewModel(coordinator)
            val event = async { viewModel.events.first() }

            viewModel.onAction(RoomsUiAction.OpenRoom("room-3"))
            advanceUntilIdle()

            assertEquals(RoomsUiEvent.NavigateToRooms, event.await())
            assertEquals(
                "This room has been archived.",
                viewModel.uiState.value.sessionErrorMessage
            )
            assertNull(viewModel.uiState.value.feedback)
        }

    @Test
    fun refreshCurrentRoomLoadsDetailsThenMembers() = runTest(dispatcher) {
        val current = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        )
        val viewModel = RoomsViewModel(coordinator)

        viewModel.onAction(RoomsUiAction.RefreshCurrentRoom)
        advanceUntilIdle()

        assertEquals(listOf("room", "members"), coordinator.refreshOrder)
        assertEquals("Room refreshed.", viewModel.uiState.value.feedback?.message)
    }

    @Test
    fun refreshThatRemovesCurrentRoomSkipsMembersAndNavigatesBack() =
        runTest(dispatcher) {
            val current = room("room-1")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(
                    rooms = listOf(current),
                    currentRoom = current
                )
            ).apply {
                refreshRoomResult = Result.success(
                    RoomSessionState(
                        lastError = RoomSessionError(
                            operation = RoomSessionOperation.REFRESH_ROOM,
                            roomId = "room-1",
                            type = "ApiException",
                            httpCode = 404,
                            apiCode = "ROOM_NOT_FOUND"
                        )
                    )
                )
            }
            val viewModel = RoomsViewModel(coordinator)
            val event = async { viewModel.events.first() }

            viewModel.onAction(RoomsUiAction.RefreshCurrentRoom)
            advanceUntilIdle()

            assertEquals(listOf("room"), coordinator.refreshOrder)
            assertEquals(RoomsUiEvent.NavigateToRooms, event.await())
            assertEquals(
                "Room was not found.",
                viewModel.uiState.value.sessionErrorMessage
            )
            assertNull(viewModel.uiState.value.feedback)
        }

    @Test
    fun deactivateSuccessNavigatesAndKeepsMembershipRoom() =
        runTest(dispatcher) {
            val current = room("room-1")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(
                    rooms = listOf(current),
                    currentRoom = current
                )
            )
            val viewModel = RoomsViewModel(coordinator)
            val event = async { viewModel.events.first() }

            viewModel.onAction(RoomsUiAction.DeactivateCurrentRoom)
            advanceUntilIdle()

            assertEquals(1, coordinator.deactivateCalls)
            assertEquals(RoomsUiEvent.NavigateToRooms, event.await())
            assertNull(viewModel.uiState.value.currentRoom)
            assertTrue(viewModel.uiState.value.roomExitPending)
            assertEquals(
                listOf(current.id),
                viewModel.uiState.value.rooms.map { it.id }
            )
            assertNull(viewModel.uiState.value.feedback)
        }

    @Test
    fun deactivateFailureKeepsRoomAndDoesNotNavigate() =
        runTest(dispatcher) {
            val current = room("room-1")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(
                    rooms = listOf(current),
                    currentRoom = current
                )
            ).apply {
                deactivateResult = Result.failure(IOException("offline"))
            }
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }

            viewModel.onAction(RoomsUiAction.DeactivateCurrentRoom)
            advanceUntilIdle()

            assertEquals(1, coordinator.deactivateCalls)
            assertEquals(current.id, viewModel.uiState.value.currentRoom?.id)
            assertFalse(viewModel.uiState.value.roomExitPending)
            assertTrue(observedEvents.isEmpty())
            assertEquals(
                "Unable to reach ServeRelay. Check your connection and try again.",
                viewModel.uiState.value.feedback?.message
            )
            collector.cancel()
        }

    @Test
    fun deactivateSuccessWithoutClearedRoomDoesNotNavigate() =
        runTest(dispatcher) {
            val current = room("room-1")
            val unchanged = RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
            val coordinator = FakeRoomSessionCoordinator(unchanged).apply {
                deactivateResult = Result.success(unchanged)
            }
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }

            viewModel.onAction(RoomsUiAction.DeactivateCurrentRoom)
            advanceUntilIdle()

            assertEquals(1, coordinator.deactivateCalls)
            assertEquals(current.id, viewModel.uiState.value.currentRoom?.id)
            assertFalse(viewModel.uiState.value.roomExitPending)
            assertTrue(observedEvents.isEmpty())
            assertEquals(
                "Unable to close the room.",
                viewModel.uiState.value.feedback?.message
            )
            collector.cancel()
        }

    @Test
    fun duplicateDeactivateIsBlockedWhileFirstRequestIsRunning() =
        runTest(dispatcher) {
            val current = room("room-1")
            val gate = CompletableDeferred<Unit>()
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(
                    rooms = listOf(current),
                    currentRoom = current
                )
            ).apply {
                deactivateRoomGate = gate
            }
            val viewModel = RoomsViewModel(coordinator)
            val event = async { viewModel.events.first() }

            viewModel.onAction(RoomsUiAction.DeactivateCurrentRoom)
            runCurrent()
            viewModel.onAction(RoomsUiAction.DeactivateCurrentRoom)
            runCurrent()

            assertEquals(1, coordinator.deactivateCalls)
            assertEquals(
                RoomsUiOperation.DEACTIVATE_ROOM,
                viewModel.uiState.value.runningOperation
            )
            assertEquals(current.id, viewModel.uiState.value.currentRoom?.id)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, coordinator.deactivateCalls)
            assertEquals(RoomsUiEvent.NavigateToRooms, event.await())
            assertNull(viewModel.uiState.value.currentRoom)
            assertNull(viewModel.uiState.value.runningOperation)
            assertTrue(viewModel.uiState.value.roomExitPending)
            assertTrue(viewModel.uiState.value.isBusy)
        }

    @Test
    fun leaveSuccessNavigatesBackToRooms() = runTest(dispatcher) {
        val current = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        )
        val viewModel = RoomsViewModel(coordinator)
        val event = async { viewModel.events.first() }

        viewModel.onAction(RoomsUiAction.LeaveCurrentRoom)
        advanceUntilIdle()

        assertEquals(1, coordinator.leaveCalls)
        assertEquals(RoomsUiEvent.NavigateToRooms, event.await())
        assertNull(viewModel.uiState.value.currentRoom)
        assertNull(viewModel.uiState.value.feedback)
    }

    @Test
    fun archiveSuccessNavigatesBackToRooms() = runTest(dispatcher) {
        val current = room("room-1").copy(currentUserRole = RoomMemberRole.OWNER)
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                rooms = listOf(current),
                currentRoom = current
            )
        )
        val viewModel = RoomsViewModel(coordinator)
        val event = async { viewModel.events.first() }

        viewModel.onAction(RoomsUiAction.ArchiveCurrentRoom)
        advanceUntilIdle()

        assertEquals(1, coordinator.archiveCalls)
        assertEquals(RoomsUiEvent.NavigateToRooms, event.await())
        assertNull(viewModel.uiState.value.currentRoom)
        assertNull(viewModel.uiState.value.feedback)
    }

    @Test
    fun apiFailureIsExposedWithoutLeakingDebugPayload() = runTest(dispatcher) {
        val coordinator = FakeRoomSessionCoordinator().apply {
            loadRoomsResult = Result.failure(
                ApiException(
                    httpCode = 500,
                    apiError = ApiErrorResponse(
                        code = "INTERNAL_ERROR",
                        message = "Unable to load rooms"
                    )
                )
            )
        }
        val viewModel = RoomsViewModel(coordinator)

        viewModel.onAction(RoomsUiAction.LoadRooms)
        advanceUntilIdle()

        assertEquals(
            "ServeRelay could not complete the room operation.",
            viewModel.uiState.value.feedback?.message
        )
        assertTrue(viewModel.uiState.value.feedback?.isError == true)
    }

    @Test
    fun failureDoesNotReuseSessionErrorFromDifferentOperation() =
        runTest(dispatcher) {
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(
                    lastError = RoomSessionError(
                        operation = RoomSessionOperation.JOIN,
                        roomId = "room-old",
                        type = "ApiException",
                        apiCode = "ROOM_ARCHIVED"
                    )
                )
            ).apply {
                loadRoomsResult = Result.failure(IOException("offline"))
            }
            val viewModel = RoomsViewModel(coordinator)

            viewModel.onAction(RoomsUiAction.LoadRooms)
            advanceUntilIdle()

            assertEquals(
                "Unable to reach ServeRelay. Check your connection and try again.",
                viewModel.uiState.value.feedback?.message
            )
            assertNull(viewModel.uiState.value.sessionErrorMessage)
        }

    @Test
    fun openDoesNotReuseSessionErrorFromDifferentRoom() = runTest(dispatcher) {
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                lastError = RoomSessionError(
                    operation = RoomSessionOperation.OPEN_ROOM,
                    roomId = "room-old",
                    type = "ApiException",
                    apiCode = "ROOM_ARCHIVED"
                )
            )
        )
        val viewModel = RoomsViewModel(coordinator)
        val event = async { viewModel.events.first() }

        viewModel.onAction(RoomsUiAction.OpenRoom("room-new"))
        advanceUntilIdle()

        assertEquals(
            RoomsUiEvent.NavigateToRoom("room-new"),
            event.await()
        )
        assertNull(viewModel.uiState.value.sessionErrorMessage)
        assertNull(viewModel.uiState.value.feedback)
    }

    @Test
    fun retryableSessionErrorIsMappedToStableUiMessage() = runTest(dispatcher) {
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                lastError = RoomSessionError(
                    operation = RoomSessionOperation.LOAD_ROOMS,
                    type = IOException::class.java.simpleName,
                    retryable = true
                )
            )
        )
        val viewModel = RoomsViewModel(coordinator)
        advanceUntilIdle()

        assertEquals(
            "ServeRelay is temporarily unavailable. Try again.",
            viewModel.uiState.value.sessionErrorMessage
        )
        assertEquals(
            viewModel.uiState.value.sessionErrorMessage,
            viewModel.uiState.value.visibleErrorMessage
        )
    }

    @Test
    fun sessionErrorClearsWhenCoordinatorRecovers() = runTest(dispatcher) {
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                lastError = RoomSessionError(
                    operation = RoomSessionOperation.LOAD_ROOMS,
                    type = IOException::class.java.simpleName,
                    retryable = true
                )
            )
        )
        val viewModel = RoomsViewModel(coordinator)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sessionErrorMessage != null)

        coordinator.emit(RoomSessionState())
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.sessionErrorMessage)
    }

    @Test
    fun disconnectedStaleSnapshotRetainsConfirmedRoomState() =
        runTest(dispatcher) {
            val current = room("room-1")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(currentRoom = current)
            )
            val viewModel = RoomsViewModel(coordinator)

            coordinator.emit(
                RoomSessionState(
                    rooms = listOf(current),
                    currentRoom = current,
                    isConnected = false,
                    isStale = true
                )
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.session.isConnected)
            assertTrue(viewModel.uiState.value.session.isStale)
            assertEquals(current, viewModel.uiState.value.currentRoom)
        }

    @Test
    fun logoutResetClearsRoomDetailsAndNavigatesOnlyOnce() =
        runTest(dispatcher) {
            val current = room("room-1")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(currentRoom = current)
            )
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }

            viewModel.onAction(RoomsUiAction.OpenRoomDetails(current.id))
            advanceUntilIdle()
            coordinator.clearForLogout()
            coordinator.clearForLogout()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.currentRoom)
            assertTrue(viewModel.uiState.value.activeMembers.isEmpty())
            assertEquals(
                listOf(RoomsUiEvent.NavigateToRooms),
                observedEvents
            )
            collector.cancel()
        }

    @Test
    fun transientFormStateRestoresWithoutPersistingServerSnapshot() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val firstCoordinator = FakeRoomSessionCoordinator(
                RoomSessionState(rooms = listOf(room("server-room")))
            )
            val first = RoomsViewModel(firstCoordinator, handle)

            first.onAction(RoomsUiAction.RoomNameChanged("Draft room"))
            first.onAction(
                RoomsUiAction.CreateVisibilityChanged(
                    RoomVisibility.LOCAL_DISCOVERY
                )
            )
            first.onAction(RoomsUiAction.JoinRoomIdChanged("join-room"))

            val replacementRoom = room("replacement-room")
            val recreated = RoomsViewModel(
                FakeRoomSessionCoordinator(
                    RoomSessionState(rooms = listOf(replacementRoom))
                ),
                handle
            )
            advanceUntilIdle()

            assertEquals("Draft room", recreated.uiState.value.roomNameInput)
            assertEquals(
                RoomVisibility.LOCAL_DISCOVERY,
                recreated.uiState.value.createVisibility
            )
            assertEquals("join-room", recreated.uiState.value.joinRoomIdInput)
            assertEquals(
                listOf(replacementRoom),
                recreated.uiState.value.rooms
            )
        }

    @Test
    fun coordinatorErrorUsesPersistentErrorWithoutDuplicateFeedback() =
        runTest(dispatcher) {
            val error = RoomSessionError(
                operation = RoomSessionOperation.LOAD_ROOMS,
                type = IOException::class.java.simpleName,
                retryable = true
            )
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(lastError = error)
            ).apply {
                loadRoomsResult = Result.failure(IOException("offline"))
            }
            val viewModel = RoomsViewModel(coordinator)

            viewModel.onAction(RoomsUiAction.LoadRooms)
            advanceUntilIdle()

            assertEquals(
                "ServeRelay is temporarily unavailable. Try again.",
                viewModel.uiState.value.sessionErrorMessage
            )
            assertNull(viewModel.uiState.value.feedback)
        }

    @Test
    fun factoryCreatesRoomsViewModel() = runTest(dispatcher) {
        val factory = RoomsViewModelFactory(FakeRoomSessionCoordinator())

        val created = factory.create(RoomsViewModel::class.java)
        runCurrent()

        assertEquals(RoomsViewModel::class.java, created.javaClass)
    }

    @Test
    fun localInFlightActionSuppressesDuplicateRequestAndExposesBusyState() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val coordinator = FakeRoomSessionCoordinator().apply {
                loadRoomsGate = gate
            }
            val viewModel = RoomsViewModel(coordinator)

            viewModel.onAction(RoomsUiAction.LoadRooms)
            runCurrent()
            assertTrue(viewModel.uiState.value.isBusy)

            viewModel.onAction(RoomsUiAction.LoadRooms)
            runCurrent()
            assertEquals(1, coordinator.loadRoomsCalls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isBusy)
        }

    @Test
    fun busyCoordinatorSuppressesUiRequest() = runTest(dispatcher) {
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(
                activeOperations = setOf(RoomSessionOperation.LOAD_ROOMS)
            )
        )
        val viewModel = RoomsViewModel(coordinator)
        advanceUntilIdle()

        viewModel.onAction(RoomsUiAction.LoadRooms)
        advanceUntilIdle()

        assertEquals(0, coordinator.loadRoomsCalls)
        assertTrue(viewModel.uiState.value.isBusy)
    }

    @Test
    fun feedbackConsumedClearsOnlyTransientFeedback() = runTest(dispatcher) {
        val coordinator = FakeRoomSessionCoordinator().apply {
            loadRoomsResult = Result.failure(IOException("offline"))
        }
        val viewModel = RoomsViewModel(coordinator)
        viewModel.onAction(RoomsUiAction.LoadRooms)
        advanceUntilIdle()
        assertEquals(
            "Unable to reach ServeRelay. Check your connection and try again.",
            viewModel.uiState.value.feedback?.message
        )

        viewModel.onAction(RoomsUiAction.FeedbackConsumed)

        assertNull(viewModel.uiState.value.feedback)
    }

    @Test
    fun busyCoordinatorDoesNotLatchRoomDetailsTargetBeforeRequest() =
        runTest(dispatcher) {
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(
                    activeOperations = setOf(RoomSessionOperation.LOAD_ROOMS)
                )
            )
            val viewModel = RoomsViewModel(coordinator)
            advanceUntilIdle()

            viewModel.onAction(RoomsUiAction.OpenRoomDetails("room-1"))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.roomDetailsRoomId)
            assertEquals(0, coordinator.openCalls)

            coordinator.emit(RoomSessionState())
            advanceUntilIdle()
            viewModel.onAction(RoomsUiAction.OpenRoomDetails("room-1"))
            advanceUntilIdle()

            assertEquals("room-1", viewModel.uiState.value.roomDetailsRoomId)
            assertEquals(1, coordinator.openCalls)
        }

    @Test
    fun operationInProgressRaceRetriesWithoutLatchingTransientError() =
        runTest(dispatcher) {
            val coordinator = FakeRoomSessionCoordinator().apply {
                openRoomBusyFailuresRemaining = 1
            }
            val viewModel = RoomsViewModel(coordinator)

            viewModel.onAction(RoomsUiAction.OpenRoomDetails("room-1"))
            advanceUntilIdle()

            assertEquals(1, coordinator.openCalls)
            assertNull(viewModel.uiState.value.feedback)
            assertNull(viewModel.uiState.value.sessionErrorMessage)

            coordinator.emit(RoomSessionState())
            advanceUntilIdle()
            viewModel.onAction(RoomsUiAction.OpenRoomDetails("room-1"))
            advanceUntilIdle()

            assertEquals(2, coordinator.openCalls)
            assertEquals("room-1", viewModel.uiState.value.roomDetails?.id)
        }

    @Test
    fun rapidOpenActionsDispatchOnlyOneCoordinatorRequest() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val coordinator = FakeRoomSessionCoordinator().apply {
                openRoomGate = gate
            }
            val viewModel = RoomsViewModel(coordinator)

            viewModel.onAction(RoomsUiAction.OpenRoom("room-1"))
            runCurrent()
            viewModel.onAction(RoomsUiAction.OpenRoom("room-1"))
            runCurrent()

            assertEquals(1, coordinator.openCalls)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun rapidCreateActionsDispatchOnlyOneCoordinatorRequest() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val coordinator = FakeRoomSessionCoordinator().apply {
                createRoomGate = gate
            }
            val viewModel = RoomsViewModel(coordinator)

            viewModel.onAction(RoomsUiAction.CreateRoom)
            runCurrent()
            viewModel.onAction(RoomsUiAction.CreateRoom)
            runCurrent()

            assertEquals(1, coordinator.createCalls)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun rapidJoinActionsDispatchOnlyOneCoordinatorRequest() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val coordinator = FakeRoomSessionCoordinator().apply {
                joinRoomGate = gate
            }
            val viewModel = RoomsViewModel(coordinator)
            viewModel.onAction(RoomsUiAction.JoinRoomIdChanged("room-1"))

            viewModel.onAction(RoomsUiAction.JoinLocalDiscoveryRoom)
            runCurrent()
            viewModel.onAction(RoomsUiAction.JoinLocalDiscoveryRoom)
            runCurrent()

            assertEquals(1, coordinator.joinCalls)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun repeatedLeaveConfirmationDispatchesOnlyOneCoordinatorRequest() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val member = room("room-1")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(currentRoom = member)
            ).apply {
                leaveRoomGate = gate
            }
            val viewModel = RoomsViewModel(coordinator)
            viewModel.onAction(RoomsUiAction.OpenRoomDetails(member.id))
            advanceUntilIdle()
            viewModel.onAction(RoomsUiAction.RequestLeaveConfirmation)

            viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
            runCurrent()
            viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
            runCurrent()

            assertEquals(1, coordinator.leaveCalls)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun repeatedArchiveConfirmationDispatchesOnlyOneCoordinatorRequest() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val owner = room("room-1").copy(
                currentUserRole = RoomMemberRole.OWNER
            )
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(currentRoom = owner)
            ).apply {
                archiveRoomGate = gate
            }
            val viewModel = RoomsViewModel(coordinator)
            viewModel.onAction(RoomsUiAction.OpenRoomDetails(owner.id))
            advanceUntilIdle()
            viewModel.onAction(RoomsUiAction.RequestArchiveConfirmation)

            viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
            runCurrent()
            viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
            runCurrent()

            assertEquals(1, coordinator.archiveCalls)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun screenScopeCancellationCancelsInFlightOpenRequest() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val coordinator = FakeRoomSessionCoordinator().apply {
                openRoomGate = gate
            }
            val viewModel = RoomsViewModel(coordinator)

            viewModel.onAction(RoomsUiAction.OpenRoom("room-1"))
            runCurrent()
            assertTrue(viewModel.uiState.value.isBusy)

            viewModel.viewModelScope.cancel()
            runCurrent()

            assertTrue(coordinator.openCancelled)
            assertFalse(viewModel.uiState.value.isBusy)
        }

    @Test
    fun roomDisappearingDuringRefreshNavigatesOnlyOnce() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val current = room("room-1")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(currentRoom = current)
            ).apply {
                refreshRoomGate = gate
            }
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }
            viewModel.onAction(RoomsUiAction.OpenRoomDetails(current.id))
            advanceUntilIdle()

            viewModel.onAction(RoomsUiAction.RefreshCurrentRoom)
            runCurrent()
            coordinator.emit(RoomSessionState())
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf(RoomsUiEvent.NavigateToRooms),
                observedEvents
            )
            collector.cancel()
        }

    @Test
    fun roomDetailsOpenUsesRouteIdOnceWithoutSameRouteNavigation() =
        runTest(dispatcher) {
            val current = room("room-1")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(currentRoom = current)
            )
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }

            viewModel.onAction(RoomsUiAction.OpenRoomDetails("  room-1  "))
            viewModel.onAction(RoomsUiAction.OpenRoomDetails("room-1"))
            advanceUntilIdle()

            assertEquals(0, coordinator.openCalls)
            assertEquals("room-1", viewModel.uiState.value.roomDetailsRoomId)
            assertEquals(current.id, viewModel.uiState.value.roomDetails?.id)
            assertTrue(observedEvents.isEmpty())
            collector.cancel()
        }

    @Test
    fun unavailableRoomDetailsNavigatesBackOnlyOnce() = runTest(dispatcher) {
        val unavailable = RoomSessionState(
            lastError = RoomSessionError(
                operation = RoomSessionOperation.OPEN_ROOM,
                roomId = "room-missing",
                type = "ApiException",
                httpCode = 409,
                apiCode = "ROOM_ARCHIVED"
            )
        )
        val coordinator = FakeRoomSessionCoordinator().apply {
            openRoomResult = Result.success(unavailable)
        }
        val viewModel = RoomsViewModel(coordinator)
        val observedEvents = mutableListOf<RoomsUiEvent>()
        val collector = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            viewModel.events.collect { observedEvents += it }
        }

        viewModel.onAction(RoomsUiAction.OpenRoomDetails("room-missing"))
        advanceUntilIdle()
        coordinator.emit(unavailable)
        advanceUntilIdle()

        assertEquals(1, coordinator.openCalls)
        assertEquals(listOf(RoomsUiEvent.NavigateToRooms), observedEvents)
        assertEquals(
            "This room has been archived.",
            viewModel.uiState.value.sessionErrorMessage
        )
        assertNull(viewModel.uiState.value.feedback)
        collector.cancel()
    }

    @Test
    fun roomDetailsRetryRepeatsOpenAfterFailure() = runTest(dispatcher) {
        val current = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = current, isStale = true)
        ).apply {
            openRoomResult = Result.failure(IOException("offline"))
        }
        val viewModel = RoomsViewModel(coordinator)

        viewModel.onAction(RoomsUiAction.OpenRoomDetails("room-1"))
        advanceUntilIdle()
        assertEquals(1, coordinator.openCalls)

        viewModel.onAction(RoomsUiAction.RetryRoomDetails)
        advanceUntilIdle()

        assertEquals(2, coordinator.openCalls)
        assertEquals(current.id, viewModel.uiState.value.roomDetails?.id)
    }

    @Test
    fun ownerCanRequestArchiveButNotLeaveConfirmation() = runTest(dispatcher) {
        val owner = room("room-1").copy(currentUserRole = RoomMemberRole.OWNER)
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = owner)
        )
        val viewModel = RoomsViewModel(coordinator)
        viewModel.onAction(RoomsUiAction.OpenRoomDetails(owner.id))
        advanceUntilIdle()

        viewModel.onAction(RoomsUiAction.RequestLeaveConfirmation)
        assertNull(viewModel.uiState.value.confirmation)
        assertTrue(viewModel.uiState.value.feedback?.isError == true)

        viewModel.onAction(RoomsUiAction.RequestArchiveConfirmation)
        assertEquals(
            RoomActionConfirmation.ARCHIVE,
            viewModel.uiState.value.confirmation
        )
    }

    @Test
    fun memberCanRequestLeaveButNotArchiveConfirmation() = runTest(dispatcher) {
        val member = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = member)
        )
        val viewModel = RoomsViewModel(coordinator)
        viewModel.onAction(RoomsUiAction.OpenRoomDetails(member.id))
        advanceUntilIdle()

        viewModel.onAction(RoomsUiAction.RequestArchiveConfirmation)
        assertNull(viewModel.uiState.value.confirmation)
        assertTrue(viewModel.uiState.value.feedback?.isError == true)

        viewModel.onAction(RoomsUiAction.RequestLeaveConfirmation)
        assertEquals(
            RoomActionConfirmation.LEAVE,
            viewModel.uiState.value.confirmation
        )
    }

    @Test
    fun externalRoomDisappearanceNavigatesBackOnlyOnce() = runTest(dispatcher) {
        val current = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = current)
        )
        val viewModel = RoomsViewModel(coordinator)
        val observedEvents = mutableListOf<RoomsUiEvent>()
        val collector = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            viewModel.events.collect { observedEvents += it }
        }
        viewModel.onAction(RoomsUiAction.OpenRoomDetails(current.id))
        advanceUntilIdle()

        coordinator.emit(RoomSessionState())
        coordinator.emit(RoomSessionState())
        advanceUntilIdle()

        assertEquals(listOf(RoomsUiEvent.NavigateToRooms), observedEvents)
        collector.cancel()
    }

    @Test
    fun archivedCurrentRoomNavigatesBackOnlyOnce() = runTest(dispatcher) {
        val current = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = current)
        )
        val viewModel = RoomsViewModel(coordinator)
        val observedEvents = mutableListOf<RoomsUiEvent>()
        val collector = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            viewModel.events.collect { observedEvents += it }
        }
        viewModel.onAction(RoomsUiAction.OpenRoomDetails(current.id))
        advanceUntilIdle()

        coordinator.emit(
            RoomSessionState(
                currentRoom = current.copy(status = RoomStatus.ARCHIVED)
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(RoomsUiEvent.NavigateToRooms), observedEvents)
        collector.cancel()
    }

    @Test
    fun successfulArchiveFromDetailsNavigatesOnceWithoutStaleFeedback() =
        runTest(dispatcher) {
            val owner = room("room-1").copy(
                currentUserRole = RoomMemberRole.OWNER
            )
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(currentRoom = owner)
            )
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }
            viewModel.onAction(RoomsUiAction.OpenRoomDetails(owner.id))
            advanceUntilIdle()
            viewModel.onAction(RoomsUiAction.RequestArchiveConfirmation)

            viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
            advanceUntilIdle()

            assertEquals(1, coordinator.archiveCalls)
            assertEquals(listOf(RoomsUiEvent.NavigateToRooms), observedEvents)
            assertNull(viewModel.uiState.value.currentRoom)
            assertNull(viewModel.uiState.value.feedback)
            collector.cancel()
        }

    @Test
    fun failedLeaveKeepsRoomAndDoesNotNavigate() = runTest(dispatcher) {
        val member = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = member)
        ).apply {
            leaveResult = Result.failure(IOException("offline"))
        }
        val viewModel = RoomsViewModel(coordinator)
        val observedEvents = mutableListOf<RoomsUiEvent>()
        val collector = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            viewModel.events.collect { observedEvents += it }
        }
        viewModel.onAction(RoomsUiAction.OpenRoomDetails(member.id))
        advanceUntilIdle()
        viewModel.onAction(RoomsUiAction.RequestLeaveConfirmation)

        viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
        advanceUntilIdle()

        assertEquals(1, coordinator.leaveCalls)
        assertEquals(member.id, viewModel.uiState.value.currentRoom?.id)
        assertEquals(
            RoomActionConfirmation.LEAVE,
            viewModel.uiState.value.confirmation
        )
        assertTrue(viewModel.uiState.value.feedback?.isError == true)
        assertTrue(observedEvents.isEmpty())
        collector.cancel()
    }

    @Test
    fun leaveSuccessWithoutClearedCurrentRoomDoesNotNavigate() =
        runTest(dispatcher) {
            val member = room("room-1")
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(currentRoom = member)
            ).apply {
                leaveResult = Result.success(
                    RoomSessionState(currentRoom = member)
                )
            }
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }
            viewModel.onAction(RoomsUiAction.OpenRoomDetails(member.id))
            advanceUntilIdle()
            viewModel.onAction(RoomsUiAction.RequestLeaveConfirmation)

            viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
            advanceUntilIdle()

            assertEquals(1, coordinator.leaveCalls)
            assertEquals(member.id, viewModel.uiState.value.currentRoom?.id)
            assertEquals(
                RoomActionConfirmation.LEAVE,
                viewModel.uiState.value.confirmation
            )
            assertEquals(
                "Unable to leave the room.",
                viewModel.uiState.value.feedback?.message
            )
            assertTrue(observedEvents.isEmpty())
            collector.cancel()
        }

    @Test
    fun successfulLeaveFromDetailsNavigatesOnce() = runTest(dispatcher) {
        val member = room("room-1")
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = member)
        )
        val viewModel = RoomsViewModel(coordinator)
        val observedEvents = mutableListOf<RoomsUiEvent>()
        val collector = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            viewModel.events.collect { observedEvents += it }
        }
        viewModel.onAction(RoomsUiAction.OpenRoomDetails(member.id))
        advanceUntilIdle()
        viewModel.onAction(RoomsUiAction.RequestLeaveConfirmation)

        viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
        advanceUntilIdle()

        assertEquals(1, coordinator.leaveCalls)
        assertEquals(listOf(RoomsUiEvent.NavigateToRooms), observedEvents)
        assertNull(viewModel.uiState.value.currentRoom)
        assertNull(viewModel.uiState.value.feedback)
        collector.cancel()
    }

    @Test
    fun archiveSuccessWithoutClearedCurrentRoomDoesNotNavigate() =
        runTest(dispatcher) {
            val owner = room("room-1").copy(
                currentUserRole = RoomMemberRole.OWNER
            )
            val coordinator = FakeRoomSessionCoordinator(
                RoomSessionState(currentRoom = owner)
            ).apply {
                archiveResult = Result.success(
                    RoomSessionState(currentRoom = owner)
                )
            }
            val viewModel = RoomsViewModel(coordinator)
            val observedEvents = mutableListOf<RoomsUiEvent>()
            val collector = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                viewModel.events.collect { observedEvents += it }
            }
            viewModel.onAction(RoomsUiAction.OpenRoomDetails(owner.id))
            advanceUntilIdle()
            viewModel.onAction(RoomsUiAction.RequestArchiveConfirmation)

            viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
            advanceUntilIdle()

            assertEquals(1, coordinator.archiveCalls)
            assertEquals(owner.id, viewModel.uiState.value.currentRoom?.id)
            assertEquals(
                RoomActionConfirmation.ARCHIVE,
                viewModel.uiState.value.confirmation
            )
            assertEquals(
                "Unable to archive the room.",
                viewModel.uiState.value.feedback?.message
            )
            assertTrue(observedEvents.isEmpty())
            collector.cancel()
        }

    @Test
    fun failedArchiveKeepsRoomAndDoesNotNavigate() = runTest(dispatcher) {
        val owner = room("room-1").copy(
            currentUserRole = RoomMemberRole.OWNER
        )
        val coordinator = FakeRoomSessionCoordinator(
            RoomSessionState(currentRoom = owner)
        ).apply {
            archiveResult = Result.failure(IOException("offline"))
        }
        val viewModel = RoomsViewModel(coordinator)
        val observedEvents = mutableListOf<RoomsUiEvent>()
        val collector = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            viewModel.events.collect { observedEvents += it }
        }
        viewModel.onAction(RoomsUiAction.OpenRoomDetails(owner.id))
        advanceUntilIdle()
        viewModel.onAction(RoomsUiAction.RequestArchiveConfirmation)

        viewModel.onAction(RoomsUiAction.ConfirmRoomAction)
        advanceUntilIdle()

        assertEquals(1, coordinator.archiveCalls)
        assertEquals(owner.id, viewModel.uiState.value.currentRoom?.id)
        assertEquals(
            RoomActionConfirmation.ARCHIVE,
            viewModel.uiState.value.confirmation
        )
        assertTrue(viewModel.uiState.value.feedback?.isError == true)
        assertTrue(observedEvents.isEmpty())
        collector.cancel()
    }

    private class FakeRoomSessionCoordinator(
        initialState: RoomSessionState = RoomSessionState()
    ) : RoomSessionCoordinator {

        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<RoomSessionState> = mutableState

        var createdName: String? = null
        var createdVisibility: RoomVisibility? = null
        var joinedRoomId: String? = null
        var openedRoomId: String? = null
        var openCalls = 0
        var loadRoomsCalls = 0
        var createCalls = 0
        var joinCalls = 0
        var deactivateCalls = 0
        var leaveCalls = 0
        var archiveCalls = 0
        val refreshOrder = mutableListOf<String>()

        var loadRoomsGate: CompletableDeferred<Unit>? = null
        var openRoomGate: CompletableDeferred<Unit>? = null
        var createRoomGate: CompletableDeferred<Unit>? = null
        var joinRoomGate: CompletableDeferred<Unit>? = null
        var deactivateRoomGate: CompletableDeferred<Unit>? = null
        var refreshRoomGate: CompletableDeferred<Unit>? = null
        var leaveRoomGate: CompletableDeferred<Unit>? = null
        var archiveRoomGate: CompletableDeferred<Unit>? = null
        var openCancelled = false
        var openRoomBusyFailuresRemaining = 0
        var loadRoomsResult: Result<List<Room>> = Result.success(emptyList())
        var openRoomResult: Result<RoomSessionState>? = null
        var createResult: Result<RoomSessionState>? = null
        var joinResult: Result<RoomSessionState>? = null
        var deactivateResult: Result<RoomSessionState>? = null
        var refreshRoomResult: Result<RoomSessionState>? = null
        var refreshMembersResult: Result<RoomSessionState>? = null
        var leaveResult: Result<RoomSessionState>? = null
        var archiveResult: Result<RoomSessionState>? = null

        fun emit(state: RoomSessionState) {
            mutableState.value = state
        }

        override suspend fun restoreFromBootstrap(
            bootstrap: SessionBootstrapResponse
        ): Result<RoomSessionState> = Result.success(mutableState.value)

        override fun markDisconnected() = Unit

        override fun clearForLogout() {
            mutableState.value = RoomSessionState()
        }

        override fun resetAfterStartupFailure() {
            mutableState.value = RoomSessionState()
        }

        override suspend fun reconnect(): Result<RoomSessionState> =
            Result.success(mutableState.value)

        override suspend fun loadRooms(): Result<List<Room>> {
            loadRoomsCalls += 1
            loadRoomsGate?.await()
            return loadRoomsResult.onSuccess { rooms ->
                mutableState.value = mutableState.value.copy(rooms = rooms)
            }
        }

        override suspend fun openRoom(roomId: String): Result<RoomSessionState> {
            openCalls += 1
            openedRoomId = roomId
            try {
                openRoomGate?.await()
            } catch (exception: CancellationException) {
                openCancelled = true
                throw exception
            }
            if (openRoomBusyFailuresRemaining > 0) {
                openRoomBusyFailuresRemaining -= 1
                val exception = IllegalStateException("busy")
                emit(
                    state.value.copy(
                        lastError = RoomSessionError(
                            operation = RoomSessionOperation.OPEN_ROOM,
                            roomId = roomId,
                            type = "RoomOperationInProgressException"
                        )
                    )
                )
                return Result.failure(exception)
            }
            val configured = openRoomResult
            if (configured != null) {
                configured.onSuccess(::emit)
                return configured
            }

            val selected = mutableState.value.currentRoom
                ?.takeIf { current -> current.id == roomId }
                ?: room(roomId)
            val next = mutableState.value.copy(
                rooms = mutableState.value.rooms
                    .filterNot { it.id == roomId } + selected,
                currentRoom = selected,
                lastError = null
            )
            emit(next)
            return Result.success(next)
        }

        override suspend fun activateRoom(
            roomId: String
        ): Result<RoomSessionState> = Result.success(state.value)

        override suspend fun deactivateCurrentRoom(): Result<RoomSessionState> {
            deactivateCalls += 1
            deactivateRoomGate?.await()
            deactivateResult?.let { result ->
                result.onSuccess(::emit)
                return result
            }

            val next = mutableState.value.copy(
                currentRoom = null,
                activeMembers = emptyList(),
                lastError = null
            )
            emit(next)
            return Result.success(next)
        }

        override suspend fun refreshCurrentRoom(): Result<RoomSessionState> {
            refreshOrder += "room"
            refreshRoomGate?.await()
            val result = refreshRoomResult ?: Result.success(mutableState.value)
            result.onSuccess(::emit)
            return result
        }

        override suspend fun refreshActiveMembers(): Result<RoomSessionState> {
            refreshOrder += "members"
            val result = refreshMembersResult ?: Result.success(mutableState.value)
            result.onSuccess(::emit)
            return result
        }

        override suspend fun createRoom(
            name: String?,
            visibility: RoomVisibility
        ): Result<RoomSessionState> {
            createCalls += 1
            createdName = name
            createdVisibility = visibility
            createRoomGate?.await()
            createResult?.let { result ->
                result.onSuccess(::emit)
                return result
            }
            val created = room("created-room").copy(
                name = name,
                visibility = visibility,
                currentUserRole = RoomMemberRole.OWNER
            )
            val next = mutableState.value.copy(
                rooms = mutableState.value.rooms
                    .filterNot { it.id == created.id } + created,
                lastError = null
            )
            emit(next)
            return Result.success(next)
        }

        override suspend fun joinLocalDiscoveryRoom(
            roomId: String
        ): Result<RoomSessionState> {
            joinCalls += 1
            joinedRoomId = roomId
            joinRoomGate?.await()
            joinResult?.let { result ->
                result.onSuccess(::emit)
                return result
            }
            val joined = room(roomId)
            val next = mutableState.value.copy(
                rooms = mutableState.value.rooms
                    .filterNot { it.id == joined.id } + joined,
                lastError = null
            )
            emit(next)
            return Result.success(next)
        }

        override suspend fun leaveCurrentRoom(): Result<RoomSessionState> {
            leaveCalls += 1
            leaveRoomGate?.await()
            leaveResult?.let { return it }
            val currentId = mutableState.value.currentRoom?.id
            val next = mutableState.value.copy(
                rooms = mutableState.value.rooms.filterNot { it.id == currentId },
                currentRoom = null,
                activeMembers = emptyList()
            )
            emit(next)
            return Result.success(next)
        }

        override suspend fun archiveCurrentRoom(): Result<RoomSessionState> {
            archiveCalls += 1
            archiveRoomGate?.await()
            archiveResult?.let { return it }
            val next = RoomSessionState()
            emit(next)
            return Result.success(next)
        }
    }

    private companion object {
        fun room(id: String): Room = Room(
            id = id,
            ownerUserId = "owner-1",
            ownerDeviceId = "device-1",
            name = "Room $id",
            status = RoomStatus.ACTIVE,
            visibility = RoomVisibility.LOCAL_DISCOVERY,
            createdAt = "2026-08-01T10:00:00",
            updatedAt = "2026-08-01T10:00:00",
            archivedAt = null,
            currentUserRole = RoomMemberRole.MEMBER,
            activeMemberCount = 1
        )
    }
}
