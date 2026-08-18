package mme.corp.audioshare.presence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceLifecycleManagerTest {

    @Test
    fun runtimeStartsOnlyAfterBootstrapAndForegroundReconciliation() {
        val coordinator = FakeCoordinator()
        val manager = PresenceLifecycleManager(coordinator)

        assertFalse(manager.onAppForeground())
        assertEquals(emptyList<Boolean>(), coordinator.starts)

        manager.activateAfterInitialHeartbeat()
        assertEquals(listOf(false), coordinator.starts)

        manager.onAppBackground()
        assertEquals(1, coordinator.stopCount)

        assertTrue(manager.onAppForeground())
        assertEquals(listOf(false), coordinator.starts)

        manager.resumeAfterReconciliation()
        assertEquals(listOf(false, false), coordinator.starts)

        manager.stop()
        assertFalse(manager.onAppForeground())
        manager.resumeAfterReconciliation()

        assertEquals(listOf(false, false), coordinator.starts)
        assertEquals(2, coordinator.stopCount)
    }

    @Test
    fun reconciliationCompletionDoesNotResumeHeartbeatAfterBackground() {
        val coordinator = FakeCoordinator()
        val manager = PresenceLifecycleManager(coordinator)

        manager.onAppForeground()
        manager.activateAfterInitialHeartbeat()
        manager.onAppBackground()

        manager.resumeAfterReconciliation()

        assertEquals(listOf(false), coordinator.starts)
        assertEquals(1, coordinator.stopCount)
    }

    private class FakeCoordinator : PresenceHeartbeatCoordinator {
        override val confirmedPresence: StateFlow<PresenceSnapshot?> =
            MutableStateFlow<PresenceSnapshot?>(null)
        override val desiredState: StateFlow<PresenceState?> =
            MutableStateFlow<PresenceState?>(null)
        override val terminalFailures: Flow<PresenceHeartbeatFailure> = emptyFlow()

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
