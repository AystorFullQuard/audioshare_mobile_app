package mme.corp.audioshare.presence

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.repository.PresenceHeartbeatClient
import mme.corp.audioshare.exception.ApiErrorResponse
import mme.corp.audioshare.exception.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.ArrayDeque
import kotlin.time.Duration.Companion.milliseconds

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

        advanceTimeBy(30_000.milliseconds)
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

        advanceTimeBy(2_000.milliseconds)
        runCurrent()

        assertTrue(result.await().isSuccess)
        assertEquals(2, client.requestedStates.size)
        assertEquals(PresenceState.ONLINE, coordinator.confirmedPresence.value?.state)
    }


    @Test
    fun exhaustedRetryableFailuresUseEveryConfiguredDelayAndOneFinalAttempt() = runTest {
        val client = FakePresenceHeartbeatClient().apply {
            enqueue(Result.failure(IOException("offline-1")))
            enqueue(Result.failure(IOException("offline-2")))
            enqueue(Result.failure(IOException("offline-3")))
        }
        val coordinator = DefaultPresenceHeartbeatCoordinator(
            presenceClient = client,
            scope = backgroundScope,
            retryDelaysMillis = listOf(100, 200)
        )

        val result = async { coordinator.heartbeatNow() }

        runCurrent()
        assertEquals(1, client.requestedStates.size)
        assertFalse(result.isCompleted)

        advanceTimeBy(100.milliseconds)
        runCurrent()
        assertEquals(2, client.requestedStates.size)
        assertFalse(result.isCompleted)

        advanceTimeBy(200.milliseconds)
        runCurrent()

        assertTrue(result.await().isFailure)
        assertEquals(3, client.requestedStates.size)
    }

    @Test
    fun explicitPresenceStateInvalidIsCallerOwnedAndClearsDesiredState() = runTest {
        val client = FakePresenceHeartbeatClient().apply {
            enqueue(Result.failure(presenceStateInvalid()))
        }
        val coordinator = DefaultPresenceHeartbeatCoordinator(
            presenceClient = client,
            scope = backgroundScope,
            retryDelaysMillis = emptyList()
        )
        coordinator.setDesiredState(PresenceState.IN_ROOM)
        var terminalEventObserved = false
        val collector = backgroundScope.launch {
            coordinator.terminalFailures.collect { terminalEventObserved = true }
        }
        runCurrent()

        val result = coordinator.heartbeatNow()
        runCurrent()

        assertTrue(result.isFailure)
        assertEquals(null, coordinator.desiredState.value)
        assertFalse(terminalEventObserved)
        collector.cancel()
    }

    @Test
    fun runtimePresenceStateInvalidPublishesTerminalFailureAndStopsLoop() = runTest {
        val client = FakePresenceHeartbeatClient().apply {
            enqueue(Result.failure(presenceStateInvalid()))
        }
        val coordinator = DefaultPresenceHeartbeatCoordinator(
            presenceClient = client,
            scope = backgroundScope,
            heartbeatIntervalMillis = 30_000,
            retryDelaysMillis = emptyList()
        )
        coordinator.setDesiredState(PresenceState.IN_ROOM)
        val failure = async { coordinator.terminalFailures.first() }

        coordinator.start(immediate = true)
        runCurrent()

        assertEquals("PRESENCE_STATE_INVALID", failure.await().apiCode)
        assertEquals(null, coordinator.desiredState.value)
        assertEquals(1, client.requestedStates.size)

        advanceTimeBy(30_000.milliseconds)
        runCurrent()
        assertEquals(1, client.requestedStates.size)
    }

    @Test
    fun runtimeTerminalFailureCanRestartHeartbeatFromEventCollector() = runTest {
        val client = FakePresenceHeartbeatClient().apply {
            enqueue(Result.failure(presenceStateInvalid()))
            enqueue(Result.success(snapshot(PresenceState.IN_ROOM)))
        }
        val coordinator = DefaultPresenceHeartbeatCoordinator(
            presenceClient = client,
            scope = backgroundScope,
            heartbeatIntervalMillis = 100,
            retryDelaysMillis = emptyList()
        )
        coordinator.setDesiredState(PresenceState.IN_ROOM)

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.terminalFailures.first()
            coordinator.start(immediate = false)
        }

        coordinator.start(immediate = true)
        runCurrent()
        assertEquals(1, client.requestedStates.size)

        advanceTimeBy(100.milliseconds)
        runCurrent()

        assertEquals(2, client.requestedStates.size)
        collector.cancel()
        coordinator.stop()
    }

    @Test
    fun exhaustedRetryableFailureDoesNotPublishTerminalEvent() = runTest {
        val client = FakePresenceHeartbeatClient().apply {
            enqueue(Result.failure(IOException("offline")))
        }
        val coordinator = DefaultPresenceHeartbeatCoordinator(
            presenceClient = client,
            scope = backgroundScope,
            retryDelaysMillis = emptyList()
        )
        var terminalEventObserved = false
        val collector = backgroundScope.launch {
            coordinator.terminalFailures.collect { terminalEventObserved = true }
        }

        val result = coordinator.heartbeatNow()
        runCurrent()

        assertTrue(result.isFailure)
        assertFalse(terminalEventObserved)
        collector.cancel()
    }

    private fun presenceStateInvalid(): ApiException =
        ApiException(
            httpCode = 409,
            apiError = ApiErrorResponse(
                code = "PRESENCE_STATE_INVALID",
                message = "invalid"
            )
        )

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
