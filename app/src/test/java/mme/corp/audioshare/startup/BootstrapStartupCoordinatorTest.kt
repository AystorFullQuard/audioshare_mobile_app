package mme.corp.audioshare.startup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.dto.bootstrap.BootstrapResponse
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.repository.DeviceBootstrapper
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
    fun startupRestoresRoomsBeforeHeartbeatAndActivatesRuntime() = runTest {
        val events = mutableListOf<String>()
        val runtime = FakeRuntimeController(events)
        val coordinator = BootstrapStartupCoordinator(
            deviceBootstrapper = FakeDeviceBootstrapper(
                Result.success(bootstrapResponse()),
                events
            ),
            sessionBootstrapLoader = FakeSessionBootstrapLoader(events),
            roomSessionRestorer = FakeRoomRestorer(events),
            roomSessionRuntimeController = FakeRoomRuntimeController(events),
            heartbeatCoordinator = FakeHeartbeatCoordinator(
                Result.success(presenceSnapshot()),
                events
            ),
            presenceRuntimeController = runtime
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isSuccess)
        assertTrue(runtime.activated)
        assertEquals(
            listOf(
                "device-bootstrap",
                "session-bootstrap",
                "room-restore",
                "heartbeat",
                "runtime-activate"
            ),
            events
        )
    }

    @Test
    fun roomRestoreFailureDoesNotActivateRuntimeOrSendHeartbeat() = runTest {
        val events = mutableListOf<String>()
        val runtime = FakeRuntimeController(events)
        val coordinator = BootstrapStartupCoordinator(
            deviceBootstrapper = FakeDeviceBootstrapper(
                Result.success(bootstrapResponse()),
                events
            ),
            sessionBootstrapLoader = FakeSessionBootstrapLoader(events),
            roomSessionRestorer = FakeRoomRestorer(
                events,
                Result.failure(IllegalStateException("restore failed"))
            ),
            roomSessionRuntimeController = FakeRoomRuntimeController(events),
            heartbeatCoordinator = FakeHeartbeatCoordinator(
                Result.success(presenceSnapshot()),
                events
            ),
            presenceRuntimeController = runtime
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
        val coordinator = BootstrapStartupCoordinator(
            deviceBootstrapper = FakeDeviceBootstrapper(
                Result.success(bootstrapResponse()),
                events
            ),
            sessionBootstrapLoader = FakeSessionBootstrapLoader(events),
            roomSessionRestorer = FakeRoomRestorer(events),
            roomSessionRuntimeController = roomRuntime,
            heartbeatCoordinator = FakeHeartbeatCoordinator(
                Result.failure(IllegalStateException("heartbeat failed")),
                events
            ),
            presenceRuntimeController = runtime
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
        val coordinator = BootstrapStartupCoordinator(
            deviceBootstrapper = FakeDeviceBootstrapper(
                Result.success(bootstrapResponse()),
                events
            ),
            sessionBootstrapLoader = FakeSessionBootstrapLoader(events),
            roomSessionRestorer = FakeRoomRestorer(
                events,
                Result.success(
                    RoomSessionState(
                        rooms = listOf(restoredRoom),
                        currentRoom = restoredRoom
                    )
                )
            ),
            roomSessionRuntimeController = roomRuntime,
            heartbeatCoordinator = FakeHeartbeatCoordinator(
                Result.success(presenceSnapshot(currentRoomId = null)),
                events
            ),
            presenceRuntimeController = runtime
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isFailure)
        assertFalse(runtime.activated)
        assertEquals(1, roomRuntime.resetCount)
        assertEquals(
            listOf(
                "device-bootstrap",
                "session-bootstrap",
                "room-restore",
                "heartbeat",
                "room-reset"
            ),
            events
        )
    }

    @Test
    fun startupForwardsCurrentSessionBootstrapMetadata() = runTest {
        val events = mutableListOf<String>()
        val loader = CapturingSessionBootstrapLoader(events)
        val coordinator = BootstrapStartupCoordinator(
            deviceBootstrapper = FakeDeviceBootstrapper(
                Result.success(bootstrapResponse()),
                events
            ),
            sessionBootstrapLoader = loader,
            roomSessionRestorer = FakeRoomRestorer(events),
            roomSessionRuntimeController = FakeRoomRuntimeController(events),
            heartbeatCoordinator = FakeHeartbeatCoordinator(
                Result.success(presenceSnapshot()),
                events
            ),
            presenceRuntimeController = FakeRuntimeController(events)
        )

        val result = coordinator.initialize(
            BootstrapStartupRequest(
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
        )

        assertTrue(result.isSuccess)
        assertEquals("Test User", loader.displayName)
        assertEquals("Pixel 10 Pro", loader.deviceName)
        assertEquals("1.0.0", loader.appVersion)
        assertEquals(Platform.ANDROID, loader.platform)
        assertEquals("Google", loader.manufacturer)
        assertEquals("Pixel 10 Pro", loader.model)
        assertEquals("17", loader.platformVersion)
        assertEquals("en-US", loader.locale)
        assertEquals("America/Los_Angeles", loader.timezone)
        assertEquals(listOf("rooms", "presence"), loader.capabilities)
    }

    private class FakeDeviceBootstrapper(
        private val result: Result<BootstrapResponse>,
        private val events: MutableList<String>
    ) : DeviceBootstrapper {
        override suspend fun bootstrap(
            displayName: String?,
            deviceName: String?,
            appVersion: String?,
            platform: Platform
        ): Result<BootstrapResponse> {
            events += "device-bootstrap"
            return result
        }
    }

    private class FakeSessionBootstrapLoader(
        private val events: MutableList<String>
    ) : SessionBootstrapLoader {
        override suspend fun loadSessionBootstrap(
            metadata: SessionBootstrapMetadata
        ): Result<SessionBootstrapResponse> {
            events += "session-bootstrap"
            return Result.success(SessionBootstrapResponse())
        }
    }

    private class CapturingSessionBootstrapLoader(
        private val events: MutableList<String>
    ) : SessionBootstrapLoader {
        var displayName: String? = null
        var deviceName: String? = null
        var appVersion: String? = null
        var platform: Platform? = null
        var manufacturer: String? = null
        var model: String? = null
        var platformVersion: String? = null
        var locale: String? = null
        var timezone: String? = null
        var capabilities: List<String>? = null

        override suspend fun loadSessionBootstrap(
            metadata: SessionBootstrapMetadata
        ): Result<SessionBootstrapResponse> {
            displayName = metadata.displayName
            deviceName = metadata.deviceName
            appVersion = metadata.appVersion
            platform = metadata.platform
            manufacturer = metadata.manufacturer
            model = metadata.model
            platformVersion = metadata.platformVersion
            locale = metadata.locale
            timezone = metadata.timezone
            capabilities = metadata.capabilities
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
        fun bootstrapResponse() = BootstrapResponse(
            userId = "user-1",
            deviceId = "device-1",
            displayName = "Test User",
            presenceState = PresenceState.ONLINE
        )

        fun presenceSnapshot(
            currentRoomId: String? = null
        ) = PresenceSnapshot(
            userId = "user-1",
            deviceId = "device-1",
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
            ownerDeviceId = "device-1",
            name = "Room $id",
            status = RoomStatus.ACTIVE,
            visibility = RoomVisibility.LOCAL_DISCOVERY,
            createdAt = "2026-07-28T12:00:00",
            updatedAt = "2026-07-28T12:00:00",
            archivedAt = null
        )
    }
}
