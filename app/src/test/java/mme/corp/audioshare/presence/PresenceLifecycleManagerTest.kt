package mme.corp.audioshare.presence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceLifecycleManagerTest {

    @Test
    fun runtimeStartsOnlyAfterBootstrapAndWhileForeground() {
        val coordinator = FakeCoordinator()
        val manager = PresenceLifecycleManager(coordinator)

        manager.onAppForeground()
        assertEquals(emptyList<Boolean>(), coordinator.starts)

        manager.activateAfterInitialHeartbeat()
        assertEquals(listOf(false), coordinator.starts)

        manager.onAppBackground()
        assertEquals(1, coordinator.stopCount)

        manager.onAppForeground()
        assertEquals(listOf(false, true), coordinator.starts)

        manager.stop()
        manager.onAppForeground()

        assertEquals(listOf(false, true), coordinator.starts)
        assertEquals(2, coordinator.stopCount)
    }

    private class FakeCoordinator : PresenceHeartbeatCoordinator {
        override val confirmedPresence: StateFlow<PresenceSnapshot?> =
            MutableStateFlow<PresenceSnapshot?>(null)
        override val desiredState: StateFlow<PresenceState?> =
            MutableStateFlow<PresenceState?>(null)

        val starts = mutableListOf<Boolean>()
        var stopCount = 0

        override fun start(immediate: Boolean) {
            starts += immediate
        }

        override fun stop() {
            stopCount += 1
        }

        override fun setDesiredState(state: PresenceState?) = Unit

        override suspend fun heartbeatNow(): Result<PresenceSnapshot> =
            error("Not used")
    }
}
