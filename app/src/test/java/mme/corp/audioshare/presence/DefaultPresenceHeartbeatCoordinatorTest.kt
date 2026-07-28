package mme.corp.audioshare.presence

import java.io.IOException
import java.util.ArrayDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.repository.PresenceHeartbeatClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPresenceHeartbeatCoordinatorTest {

    @Test
    fun startSendsImmediateAndPeriodicHeartbeats() = runTest {
        val client = FakePresenceHeartbeatClient()
        val coordinator = DefaultPresenceHeartbeatCoordinator(
            presenceClient = client,
            scope = backgroundScope,
            heartbeatIntervalMillis = 30_000,
            retryDelaysMillis = emptyList()
        )

        coordinator.start()
        runCurrent()

        assertEquals(listOf<PresenceState?>(null), client.requestedStates)

        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(listOf(null, null), client.requestedStates)
        coordinator.stop()
    }

    @Test
    fun repeatedStartDoesNotCreateDuplicateLoop() = runTest {
        val client = FakePresenceHeartbeatClient()
        val coordinator = DefaultPresenceHeartbeatCoordinator(
            presenceClient = client,
            scope = backgroundScope,
            heartbeatIntervalMillis = 30_000,
            retryDelaysMillis = emptyList()
        )

        coordinator.start()
        coordinator.start()
        runCurrent()

        assertEquals(1, client.requestedStates.size)
        coordinator.stop()
    }

    @Test
    fun setDesiredStateSendsImmediateHeartbeat() = runTest {
        val client = FakePresenceHeartbeatClient()
        val coordinator = DefaultPresenceHeartbeatCoordinator(
            presenceClient = client,
            scope = backgroundScope,
            heartbeatIntervalMillis = 30_000,
            retryDelaysMillis = emptyList()
        )

        coordinator.start(immediate = false)
        runCurrent()

        coordinator.setDesiredState(PresenceState.STREAMING)
        runCurrent()

        assertEquals(listOf(PresenceState.STREAMING), client.requestedStates)
        assertEquals(PresenceState.STREAMING, coordinator.desiredState.value)
        coordinator.stop()
    }

    @Test
    fun transientFailureUsesConfiguredBackoff() = runTest {
        val client = FakePresenceHeartbeatClient().apply {
            enqueue(Result.failure(IOException("offline")))
            enqueue(Result.success(snapshot(PresenceState.ONLINE)))
        }
        val coordinator = DefaultPresenceHeartbeatCoordinator(
            presenceClient = client,
            scope = backgroundScope,
            retryDelaysMillis = listOf(2_000)
        )

        val result = async {
            coordinator.heartbeatNow()
        }

        runCurrent()
        assertEquals(1, client.requestedStates.size)
        assertFalse(result.isCompleted)

        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(result.await().isSuccess)
        assertEquals(2, client.requestedStates.size)
        assertEquals(PresenceState.ONLINE, coordinator.confirmedPresence.value?.state)
    }

    private class FakePresenceHeartbeatClient : PresenceHeartbeatClient {
        val requestedStates = mutableListOf<PresenceState?>()
        private val results = ArrayDeque<Result<PresenceSnapshot>>()

        fun enqueue(result: Result<PresenceSnapshot>) {
            results.addLast(result)
        }

        override suspend fun heartbeat(
            state: PresenceState?
        ): Result<PresenceSnapshot> {
            requestedStates += state
            return if (results.isEmpty()) {
                Result.success(snapshot(state ?: PresenceState.ONLINE))
            } else {
                results.removeFirst()
            }
        }
    }

    private companion object {
        fun snapshot(state: PresenceState) = PresenceSnapshot(
            userId = "user-1",
            deviceId = "device-1",
            state = state,
            currentRoomId = null,
            lastSeenAt = "2026-07-28T12:00:00"
        )
    }
}
