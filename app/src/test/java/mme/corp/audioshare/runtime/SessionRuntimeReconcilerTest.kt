package mme.corp.audioshare.runtime

import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.exception.ApiErrorResponse
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.presence.PresenceHeartbeatCoordinator
import mme.corp.audioshare.presence.PresenceHeartbeatFailure
import mme.corp.audioshare.presence.PresenceRuntimeController
import mme.corp.audioshare.room.RoomSessionCoordinator
import mme.corp.audioshare.room.RoomSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRuntimeReconcilerTest {

    @Test
    fun foregroundWithActiveRoomHeartbeatsThenRefreshesRoomAndMembers() = runTest {
        val current = room("room-1")
        val roomCoordinator = FakeRoomSessionCoordinator(
            RoomSessionState(rooms = listOf(current), currentRoom = current)
        )
        val heartbeat = FakeHeartbeatCoordinator().apply {
            enqueueHeartbeat(Result.success(snapshot("room-1")))
        }
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )

        reconciler.onAppForeground(runtimeReady = true)
        runCurrent()

        assertEquals(1, heartbeat.heartbeatCalls)
        assertEquals(1, roomCoordinator.refreshRoomCalls)
        assertEquals(1, roomCoordinator.refreshMembersCalls)
        assertEquals(0, roomCoordinator.activateCalls)
        assertEquals(1, runtime.resumeCalls)
    }

    @Test
    fun foregroundWithoutActiveRoomHeartbeatsOnlineWithoutRoomRequests() = runTest {
        val roomCoordinator = FakeRoomSessionCoordinator(RoomSessionState())
        val heartbeat = FakeHeartbeatCoordinator().apply {
            enqueueHeartbeat(Result.success(snapshot(null)))
        }
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )

        reconciler.onAppForeground(runtimeReady = true)
        runCurrent()

        assertEquals(1, heartbeat.heartbeatCalls)
        assertEquals(listOf(PresenceState.ONLINE), heartbeat.desiredStates)
        assertEquals(0, roomCoordinator.refreshRoomCalls)
        assertEquals(0, roomCoordinator.refreshMembersCalls)
        assertEquals(1, runtime.resumeCalls)
    }

    @Test
    fun foregroundRefreshThatClearsArchivedRoomConfirmsOnline() = runTest {
        val current = room("room-1")
        val heartbeat = FakeHeartbeatCoordinator().apply {
            enqueueHeartbeat(Result.success(snapshot("room-1")))
            enqueueHeartbeat(Result.success(snapshot(null)))
        }
        val roomCoordinator = FakeRoomSessionCoordinator(
            RoomSessionState(rooms = listOf(current), currentRoom = current)
        ).apply {
            refreshRoomHandler = {
                updateState(RoomSessionState())
                Result.success(state.value)
            }
        }
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )

        reconciler.onAppForeground(runtimeReady = true)
        runCurrent()

        assertEquals(1, roomCoordinator.refreshRoomCalls)
        assertEquals(0, roomCoordinator.refreshMembersCalls)
        assertEquals(listOf(PresenceState.ONLINE), heartbeat.desiredStates)
        assertEquals(2, heartbeat.heartbeatCalls)
        assertEquals(1, runtime.resumeCalls)
    }

    @Test
    fun invalidPresenceClearsMissingRoomAndConfirmsOnline() = runTest {
        val current = room("room-1")
        val heartbeat = FakeHeartbeatCoordinator().apply {
            enqueueHeartbeat(Result.failure(presenceStateInvalid()))
            enqueueHeartbeat(Result.success(snapshot(null)))
        }
        val roomCoordinator = FakeRoomSessionCoordinator(
            RoomSessionState(rooms = listOf(current), currentRoom = current)
        ).apply {
            reconnectHandler = {
                updateState(RoomSessionState())
                Result.success(state.value)
            }
        }
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )

        reconciler.onAppForeground(runtimeReady = true)
        runCurrent()

        assertEquals(1, roomCoordinator.reconnectCalls)
        assertEquals(0, roomCoordinator.activateCalls)
        assertEquals(listOf(PresenceState.ONLINE), heartbeat.desiredStates)
        assertEquals(2, heartbeat.heartbeatCalls)
        assertEquals(1, runtime.resumeCalls)
    }

    @Test
    fun invalidPresenceReactivatesValidatedCurrentRoomBeforeHeartbeatRetry() = runTest {
        val current = room("room-1")
        val heartbeat = FakeHeartbeatCoordinator().apply {
            enqueueHeartbeat(Result.failure(presenceStateInvalid()))
            enqueueHeartbeat(Result.success(snapshot("room-1")))
        }
        val roomCoordinator = FakeRoomSessionCoordinator(
            RoomSessionState(rooms = listOf(current), currentRoom = current)
        ).apply {
            onActivate = { heartbeat.setDesiredState(PresenceState.IN_ROOM) }
        }
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )

        reconciler.onAppForeground(runtimeReady = true)
        runCurrent()

        assertEquals(1, roomCoordinator.reconnectCalls)
        assertEquals(listOf("room-1"), roomCoordinator.activatedRoomIds)
        assertTrue(heartbeat.desiredStates.contains(PresenceState.IN_ROOM))
        assertEquals(2, heartbeat.heartbeatCalls)
        assertEquals(1, runtime.resumeCalls)
    }

    @Test
    fun successfulHeartbeatMismatchUsesSameRecoveryPath() = runTest {
        val current = room("room-1")
        val heartbeat = FakeHeartbeatCoordinator().apply {
            enqueueHeartbeat(Result.success(snapshot(null)))
            enqueueHeartbeat(Result.success(snapshot("room-1")))
        }
        val roomCoordinator = FakeRoomSessionCoordinator(
            RoomSessionState(rooms = listOf(current), currentRoom = current)
        ).apply {
            onActivate = { heartbeat.setDesiredState(PresenceState.IN_ROOM) }
        }
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )

        reconciler.onAppForeground(runtimeReady = true)
        runCurrent()

        assertEquals(1, roomCoordinator.reconnectCalls)
        assertEquals(1, roomCoordinator.activateCalls)
        assertEquals(2, heartbeat.heartbeatCalls)
        assertEquals(1, runtime.resumeCalls)
    }

    @Test
    fun terminalPresenceFailureTriggersRoomRecoveryWhileForeground() = runTest {
        val current = room("room-1")
        val heartbeat = FakeHeartbeatCoordinator().apply {
            enqueueHeartbeat(Result.success(snapshot("room-1")))
        }
        val roomCoordinator = FakeRoomSessionCoordinator(
            RoomSessionState(rooms = listOf(current), currentRoom = current)
        ).apply {
            onActivate = { heartbeat.setDesiredState(PresenceState.IN_ROOM) }
        }
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )
        reconciler.onAppForeground(runtimeReady = false)
        runCurrent()

        heartbeat.emitTerminalFailure(
            PresenceHeartbeatFailure(
                type = "ApiException",
                httpCode = 409,
                apiCode = "PRESENCE_STATE_INVALID"
            )
        )
        runCurrent()

        assertEquals(1, roomCoordinator.reconnectCalls)
        assertEquals(1, roomCoordinator.activateCalls)
        assertEquals(1, heartbeat.heartbeatCalls)
        assertEquals(1, runtime.resumeCalls)
    }

    @Test
    fun backgroundCancelsInFlightForegroundReconciliation() = runTest {
        val heartbeatGate = CompletableDeferred<Unit>()
        val heartbeat = FakeHeartbeatCoordinator().apply {
            heartbeatGateOverride = heartbeatGate
        }
        val roomCoordinator = FakeRoomSessionCoordinator(RoomSessionState())
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )

        reconciler.onAppForeground(runtimeReady = true)
        runCurrent()
        assertEquals(1, heartbeat.heartbeatCalls)

        reconciler.onAppBackground()
        runCurrent()

        assertEquals(1, heartbeat.cancelledHeartbeats)
        assertEquals(0, runtime.resumeCalls)
    }

    @Test
    fun repeatedInvalidHeartbeatDuringRecoveryDoesNotLoop() = runTest {
        val current = room("room-1")
        val heartbeat = FakeHeartbeatCoordinator().apply {
            enqueueHeartbeat(Result.failure(presenceStateInvalid()))
            enqueueHeartbeat(Result.failure(presenceStateInvalid()))
        }
        val roomCoordinator = FakeRoomSessionCoordinator(
            RoomSessionState(rooms = listOf(current), currentRoom = current)
        ).apply {
            onActivate = { heartbeat.setDesiredState(PresenceState.IN_ROOM) }
        }
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )

        reconciler.onAppForeground(runtimeReady = true)
        runCurrent()
        runCurrent()

        assertEquals(2, heartbeat.heartbeatCalls)
        assertEquals(1, roomCoordinator.reconnectCalls)
        assertEquals(1, roomCoordinator.activateCalls)
        assertEquals(0, runtime.resumeCalls)
    }

    @Test
    fun nonReconcilableTerminalFailureDoesNotStartRoomRecovery() = runTest {
        val roomCoordinator = FakeRoomSessionCoordinator(RoomSessionState())
        val heartbeat = FakeHeartbeatCoordinator()
        val runtime = FakePresenceRuntimeController()
        val reconciler = SessionRuntimeReconciler(
            roomSessionCoordinator = roomCoordinator,
            heartbeatCoordinator = heartbeat,
            presenceRuntimeController = runtime,
            scope = backgroundScope
        )
        reconciler.onAppForeground(runtimeReady = false)
        runCurrent()

        heartbeat.emitTerminalFailure(
            PresenceHeartbeatFailure(
                type = "ApiException",
                httpCode = 401,
                apiCode = "AUTHENTICATION_REQUIRED"
            )
        )
        runCurrent()

        assertEquals(0, heartbeat.heartbeatCalls)
        assertEquals(0, roomCoordinator.reconnectCalls)
        assertEquals(0, runtime.resumeCalls)
    }

    private class FakePresenceRuntimeController : PresenceRuntimeController {
        var resumeCalls = 0

        override fun activateAfterInitialHeartbeat() = Unit

        override fun resumeAfterReconciliation() {
            resumeCalls += 1
        }

        override fun stop() = Unit
    }

    private class FakeHeartbeatCoordinator : PresenceHeartbeatCoordinator {
        private val mutableConfirmed = MutableStateFlow<PresenceSnapshot?>(null)
        private val mutableDesired = MutableStateFlow<PresenceState?>(null)
        private val mutableTerminalFailures =
            MutableSharedFlow<PresenceHeartbeatFailure>(extraBufferCapacity = 8)
        private val heartbeatResults = ArrayDeque<Result<PresenceSnapshot>>()

        override val confirmedPresence: StateFlow<PresenceSnapshot?> =
            mutableConfirmed.asStateFlow()
        override val desiredState: StateFlow<PresenceState?> =
            mutableDesired.asStateFlow()
        override val terminalFailures: Flow<PresenceHeartbeatFailure> =
            mutableTerminalFailures

        val desiredStates = mutableListOf<PresenceState?>()
        var heartbeatCalls = 0
        var cancelledHeartbeats = 0
        var heartbeatGateOverride: CompletableDeferred<Unit>? = null

        fun enqueueHeartbeat(result: Result<PresenceSnapshot>) {
            heartbeatResults.addLast(result)
        }

        fun emitTerminalFailure(failure: PresenceHeartbeatFailure) {
            check(mutableTerminalFailures.tryEmit(failure))
        }

        override fun start(immediate: Boolean) = Unit
        override fun stop() = Unit

        override fun setDesiredState(state: PresenceState?) {
            mutableDesired.value = state
            desiredStates += state
        }

        override suspend fun heartbeatNow(): Result<PresenceSnapshot> {
            heartbeatCalls += 1
            try {
                heartbeatGateOverride?.await()
            } catch (exception: CancellationException) {
                cancelledHeartbeats += 1
                throw exception
            }
            val result = if (heartbeatResults.isEmpty()) {
                Result.success(snapshot(null))
            } else {
                heartbeatResults.removeFirst()
            }
            result.onSuccess { mutableConfirmed.value = it }
            return result
        }
    }

    private class FakeRoomSessionCoordinator(
        initialState: RoomSessionState
    ) : RoomSessionCoordinator {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<RoomSessionState> = mutableState.asStateFlow()

        var refreshRoomCalls = 0
        var refreshMembersCalls = 0
        var reconnectCalls = 0
        var activateCalls = 0
        val activatedRoomIds = mutableListOf<String>()
        var refreshRoomHandler: suspend () -> Result<RoomSessionState> = {
            Result.success(state.value)
        }
        var reconnectHandler: suspend () -> Result<RoomSessionState> = {
            Result.success(state.value)
        }
        var onActivate: (String) -> Unit = {}

        fun updateState(next: RoomSessionState) {
            mutableState.value = next
        }

        override suspend fun refreshCurrentRoom(): Result<RoomSessionState> {
            refreshRoomCalls += 1
            return refreshRoomHandler()
        }

        override suspend fun refreshActiveMembers(): Result<RoomSessionState> {
            refreshMembersCalls += 1
            return Result.success(state.value)
        }

        override suspend fun reconnect(): Result<RoomSessionState> {
            reconnectCalls += 1
            return reconnectHandler()
        }

        override suspend fun activateRoom(roomId: String): Result<RoomSessionState> {
            activateCalls += 1
            activatedRoomIds += roomId
            onActivate(roomId)
            return Result.success(state.value)
        }

        override suspend fun restoreFromBootstrap(
            bootstrap: mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
        ): Result<RoomSessionState> = Result.success(state.value)

        override fun markDisconnected() = Unit
        override fun clearForLogout() = Unit
        override fun resetAfterStartupFailure() = Unit
        override suspend fun loadRooms(): Result<List<Room>> = Result.success(state.value.rooms)
        override suspend fun openRoom(roomId: String): Result<RoomSessionState> =
            Result.success(state.value)
        override suspend fun deactivateCurrentRoom(): Result<RoomSessionState> =
            Result.success(state.value)
        override suspend fun createRoom(
            name: String?,
            visibility: RoomVisibility
        ): Result<RoomSessionState> = Result.success(state.value)
        override suspend fun joinLocalDiscoveryRoom(roomId: String): Result<RoomSessionState> =
            Result.success(state.value)
        override suspend fun leaveCurrentRoom(): Result<RoomSessionState> =
            Result.success(state.value)
        override suspend fun archiveCurrentRoom(): Result<RoomSessionState> =
            Result.success(state.value)
    }

    private companion object {
        fun room(id: String) = Room(
            id = id,
            ownerUserId = "owner-1",
            ownerDeviceId = "device-1",
            name = "Room $id",
            status = RoomStatus.ACTIVE,
            visibility = RoomVisibility.LOCAL_DISCOVERY,
            createdAt = "2026-08-18T12:00:00",
            updatedAt = "2026-08-18T12:00:00",
            archivedAt = null
        )

        fun snapshot(currentRoomId: String?) = PresenceSnapshot(
            userId = "user-1",
            deviceId = "device-1",
            state = if (currentRoomId == null) {
                PresenceState.ONLINE
            } else {
                PresenceState.IN_ROOM
            },
            currentRoomId = currentRoomId,
            lastSeenAt = "2026-08-18T12:00:00"
        )

        fun presenceStateInvalid() = ApiException(
            httpCode = 409,
            apiError = ApiErrorResponse(
                code = "PRESENCE_STATE_INVALID",
                message = "invalid room context"
            )
        )
    }
}
