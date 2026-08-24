package mme.corp.audioshare.startup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.repository.SessionBootstrapLoader
import mme.corp.audioshare.data.repository.SessionBootstrapMetadata
import mme.corp.audioshare.presence.PresenceHeartbeatCoordinator
import mme.corp.audioshare.presence.PresenceHeartbeatFailure
import mme.corp.audioshare.presence.PresenceRuntimeController
import mme.corp.audioshare.room.RoomSessionBootstrapRestorer
import mme.corp.audioshare.room.RoomSessionRuntimeController
import mme.corp.audioshare.room.RoomSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapStartupCoordinatorTest {

    @Test
    fun startupResolvesDeviceBeforeSessionBootstrapAndActivatesRuntime() = runTest {
        val events = mutableListOf<String>()
        val runtime = FakeRuntimeController(events)
        val coordinator = coordinator(
            events = events,
            runtime = runtime
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isSuccess)
        assertEquals(DEVICE_ID, result.getOrThrow().deviceId)
        assertTrue(runtime.activated)
        assertEquals(
            listOf(
                "device-resolve",
                "session-bootstrap",
                "room-restore",
                "heartbeat",
                "runtime-activate"
            ),
            events
        )
    }

    @Test
    fun deviceResolutionFailureStopsStartupBeforeSessionBootstrap() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            events = events,
            deviceResult = Result.failure(
                IllegalStateException("device registration failed")
            )
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isFailure)
        assertEquals(listOf("device-resolve"), events)
    }

    @Test
    fun sessionBootstrapFailureStopsBeforeRoomRestoreAndHeartbeat() = runTest {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            events = events,
            sessionResult = Result.failure(
                IllegalStateException("session bootstrap failed")
            )
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isFailure)
        assertEquals(
            listOf("device-resolve", "session-bootstrap"),
            events
        )
    }

    @Test
    fun roomRestoreFailureDoesNotActivateRuntimeOrSendHeartbeat() = runTest {
        val events = mutableListOf<String>()
        val runtime = FakeRuntimeController(events)
        val coordinator = coordinator(
            events = events,
            runtime = runtime,
            roomResult = Result.failure(IllegalStateException("restore failed"))
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isFailure)
        assertFalse(runtime.activated)
        assertFalse(events.contains("heartbeat"))
    }

    @Test
    fun heartbeatFailureDoesNotActivateRuntimeAndClearsRoomSession() = runTest {
        val events = mutableListOf<String>()
        val runtime = FakeRuntimeController(events)
        val roomRuntime = FakeRoomRuntimeController(events)
        val coordinator = coordinator(
            events = events,
            runtime = runtime,
            roomRuntime = roomRuntime,
            heartbeatResult = Result.failure(
                IllegalStateException("heartbeat failed")
            )
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isFailure)
        assertFalse(runtime.activated)
        assertEquals(1, roomRuntime.resetCount)
    }

    @Test
    fun confirmedPresenceMismatchRejectsStartupAndClearsRoomSession() = runTest {
        val events = mutableListOf<String>()
        val runtime = FakeRuntimeController(events)
        val roomRuntime = FakeRoomRuntimeController(events)
        val restoredRoom = room("room-1")
        val coordinator = coordinator(
            events = events,
            runtime = runtime,
            roomRuntime = roomRuntime,
            roomResult = Result.success(
                RoomSessionState(
                    rooms = listOf(restoredRoom),
                    currentRoom = restoredRoom
                )
            ),
            heartbeatResult = Result.success(
                presenceSnapshot(currentRoomId = null)
            )
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isFailure)
        assertFalse(runtime.activated)
        assertEquals(1, roomRuntime.resetCount)
        assertEquals(
            listOf(
                "device-resolve",
                "session-bootstrap",
                "room-restore",
                "heartbeat",
                "room-reset"
            ),
            events
        )
    }

    @Test
    fun startupForwardsCurrentMetadataToDeviceAndSessionSteps() = runTest {
        val events = mutableListOf<String>()
        val deviceResolver = CapturingDeviceRegistrationResolver(events)
        val loader = CapturingSessionBootstrapLoader(events)
        val coordinator = BootstrapStartupCoordinator(
            deviceRegistrationResolver = deviceResolver,
            sessionBootstrapLoader = loader,
            roomSessionRestorer = FakeRoomRestorer(events),
            roomSessionRuntimeController = FakeRoomRuntimeController(events),
            heartbeatCoordinator = FakeHeartbeatCoordinator(
                Result.success(presenceSnapshot()),
                events
            ),
            presenceRuntimeController = FakeRuntimeController(events)
        )

        val result = coordinator.initialize(startupRequest())

        assertTrue(result.isSuccess)
        assertEquals(expectedMetadata(), deviceResolver.metadata)
        assertEquals(expectedMetadata(), loader.metadata)
    }

    private fun coordinator(
        events: MutableList<String>,
        deviceResult: Result<String> = Result.success(DEVICE_ID),
        sessionResult: Result<SessionBootstrapResponse> =
            Result.success(SessionBootstrapResponse()),
        roomResult: Result<RoomSessionState> =
            Result.success(RoomSessionState()),
        heartbeatResult: Result<PresenceSnapshot> =
            Result.success(presenceSnapshot()),
        runtime: FakeRuntimeController = FakeRuntimeController(events),
        roomRuntime: FakeRoomRuntimeController =
            FakeRoomRuntimeController(events)
    ) = BootstrapStartupCoordinator(
        deviceRegistrationResolver = FakeDeviceRegistrationResolver(
            deviceResult,
            events
        ),
        sessionBootstrapLoader = FakeSessionBootstrapLoader(
            sessionResult,
            events
        ),
        roomSessionRestorer = FakeRoomRestorer(events, roomResult),
        roomSessionRuntimeController = roomRuntime,
        heartbeatCoordinator = FakeHeartbeatCoordinator(
            heartbeatResult,
            events
        ),
        presenceRuntimeController = runtime
    )

    private class FakeDeviceRegistrationResolver(
        private val result: Result<String>,
        private val events: MutableList<String>
    ) : DeviceRegistrationResolver {
        override suspend fun resolveOrRegister(
            metadata: SessionBootstrapMetadata
        ): Result<String> {
            events += "device-resolve"
            return result
        }
    }

    private class CapturingDeviceRegistrationResolver(
        private val events: MutableList<String>
    ) : DeviceRegistrationResolver {
        var metadata: SessionBootstrapMetadata? = null

        override suspend fun resolveOrRegister(
            metadata: SessionBootstrapMetadata
        ): Result<String> {
            this.metadata = metadata
            events += "device-resolve"
            return Result.success(DEVICE_ID)
        }
    }

    private class FakeSessionBootstrapLoader(
        private val result: Result<SessionBootstrapResponse>,
        private val events: MutableList<String>
    ) : SessionBootstrapLoader {
        override suspend fun loadSessionBootstrap(
            metadata: SessionBootstrapMetadata
        ): Result<SessionBootstrapResponse> {
            events += "session-bootstrap"
            return result
        }
    }

    private class CapturingSessionBootstrapLoader(
        private val events: MutableList<String>
    ) : SessionBootstrapLoader {
        var metadata: SessionBootstrapMetadata? = null

        override suspend fun loadSessionBootstrap(
            metadata: SessionBootstrapMetadata
        ): Result<SessionBootstrapResponse> {
            this.metadata = metadata
            events += "session-bootstrap"
            return Result.success(SessionBootstrapResponse())
        }
    }

    private class FakeRoomRestorer(
        private val events: MutableList<String>,
        private val result: Result<RoomSessionState> =
            Result.success(RoomSessionState())
    ) : RoomSessionBootstrapRestorer {
        override suspend fun restoreFromBootstrap(
            bootstrap: SessionBootstrapResponse
        ): Result<RoomSessionState> {
            events += "room-restore"
            return result
        }
    }

    private class FakeRoomRuntimeController(
        private val events: MutableList<String>
    ) : RoomSessionRuntimeController {
        var resetCount = 0

        override fun markDisconnected() = Unit
        override fun clearForLogout() = Unit

        override fun resetAfterStartupFailure() {
            resetCount += 1
            events += "room-reset"
        }

        override suspend fun reconnect(): Result<RoomSessionState> =
            Result.success(RoomSessionState())
    }

    private class FakeHeartbeatCoordinator(
        private val result: Result<PresenceSnapshot>,
        private val events: MutableList<String>
    ) : PresenceHeartbeatCoordinator {
        override val confirmedPresence: StateFlow<PresenceSnapshot?> =
            MutableStateFlow(null)
        override val desiredState: StateFlow<PresenceState?> =
            MutableStateFlow(null)
        override val terminalFailures: Flow<PresenceHeartbeatFailure> = emptyFlow()

        override fun start(immediate: Boolean) = Unit
        override fun stop() = Unit
        override fun setDesiredState(state: PresenceState?) = Unit
        override suspend fun heartbeatNow(): Result<PresenceSnapshot> {
            events += "heartbeat"
            return result
        }
    }

    private class FakeRuntimeController(
        private val events: MutableList<String>
    ) : PresenceRuntimeController {
        var activated = false

        override fun activateAfterInitialHeartbeat() {
            activated = true
            events += "runtime-activate"
        }

        override fun resumeAfterReconciliation() = Unit

        override fun stop() = Unit
    }

    private companion object {
        const val DEVICE_ID = "11111111-1111-1111-1111-111111111111"

        fun startupRequest() = BootstrapStartupRequest(
            displayName = "Test User",
            deviceName = "Pixel 10 Pro",
            appVersion = "1.0.0",
            platform = Platform.ANDROID,
            manufacturer = "Google",
            model = "Pixel 10 Pro",
            platformVersion = "17",
            locale = "en-US",
            timezone = "America/Los_Angeles",
            capabilities = listOf("rooms", "presence")
        )

        fun expectedMetadata() = SessionBootstrapMetadata(
            displayName = "Test User",
            deviceName = "Pixel 10 Pro",
            appVersion = "1.0.0",
            platform = Platform.ANDROID,
            manufacturer = "Google",
            model = "Pixel 10 Pro",
            platformVersion = "17",
            locale = "en-US",
            timezone = "America/Los_Angeles",
            capabilities = listOf("rooms", "presence")
        )

        fun presenceSnapshot(
            currentRoomId: String? = null
        ) = PresenceSnapshot(
            userId = "user-1",
            deviceId = DEVICE_ID,
            state = if (currentRoomId == null) {
                PresenceState.ONLINE
            } else {
                PresenceState.IN_ROOM
            },
            currentRoomId = currentRoomId,
            lastSeenAt = "2026-07-28T12:00:00"
        )

        fun room(id: String) = Room(
            id = id,
            ownerUserId = "owner-1",
            ownerDeviceId = DEVICE_ID,
            name = "Room $id",
            status = RoomStatus.ACTIVE,
            visibility = RoomVisibility.LOCAL_DISCOVERY,
            createdAt = "2026-07-28T12:00:00",
            updatedAt = "2026-07-28T12:00:00",
            archivedAt = null
        )
    }
}
