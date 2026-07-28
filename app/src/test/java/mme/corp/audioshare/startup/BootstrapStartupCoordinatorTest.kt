package mme.corp.audioshare.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.dto.bootstrap.BootstrapResponse
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.repository.DeviceBootstrapper
import mme.corp.audioshare.presence.PresenceHeartbeatCoordinator
import mme.corp.audioshare.presence.PresenceRuntimeController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapStartupCoordinatorTest {

    @Test
    fun successfulBootstrapAndHeartbeatActivatesRuntime() = runTest {
        val runtime = FakeRuntimeController()
        val coordinator = BootstrapStartupCoordinator(
            deviceBootstrapper = FakeDeviceBootstrapper(Result.success(bootstrapResponse())),
            heartbeatCoordinator = FakeHeartbeatCoordinator(Result.success(presenceSnapshot())),
            presenceRuntimeController = runtime
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isSuccess)
        assertTrue(runtime.activated)
    }

    @Test
    fun heartbeatFailureDoesNotActivateRuntime() = runTest {
        val runtime = FakeRuntimeController()
        val coordinator = BootstrapStartupCoordinator(
            deviceBootstrapper = FakeDeviceBootstrapper(Result.success(bootstrapResponse())),
            heartbeatCoordinator = FakeHeartbeatCoordinator(
                Result.failure(IllegalStateException("heartbeat failed"))
            ),
            presenceRuntimeController = runtime
        )

        val result = coordinator.initialize(BootstrapStartupRequest())

        assertTrue(result.isFailure)
        assertFalse(runtime.activated)
    }

    private class FakeDeviceBootstrapper(
        private val result: Result<BootstrapResponse>
    ) : DeviceBootstrapper {
        override suspend fun bootstrap(
            displayName: String?,
            deviceName: String?,
            appVersion: String?,
            platform: Platform
        ): Result<BootstrapResponse> = result
    }

    private class FakeHeartbeatCoordinator(
        private val result: Result<PresenceSnapshot>
    ) : PresenceHeartbeatCoordinator {
        override val confirmedPresence: StateFlow<PresenceSnapshot?> =
            MutableStateFlow<PresenceSnapshot?>(null)
        override val desiredState: StateFlow<PresenceState?> =
            MutableStateFlow<PresenceState?>(null)

        override fun start(immediate: Boolean) = Unit
        override fun stop() = Unit
        override fun setDesiredState(state: PresenceState?) = Unit
        override suspend fun heartbeatNow(): Result<PresenceSnapshot> = result
    }

    private class FakeRuntimeController : PresenceRuntimeController {
        var activated = false

        override fun activateAfterInitialHeartbeat() {
            activated = true
        }

        override fun stop() = Unit
    }

    private companion object {
        fun bootstrapResponse() = BootstrapResponse(
            userId = "user-1",
            deviceId = "device-1",
            displayName = "Test User",
            presenceState = PresenceState.ONLINE
        )

        fun presenceSnapshot() = PresenceSnapshot(
            userId = "user-1",
            deviceId = "device-1",
            state = PresenceState.ONLINE,
            currentRoomId = null,
            lastSeenAt = "2026-07-28T12:00:00"
        )
    }
}
