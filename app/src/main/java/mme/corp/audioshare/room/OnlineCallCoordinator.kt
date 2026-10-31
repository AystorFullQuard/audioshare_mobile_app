package mme.corp.audioshare.room

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mme.corp.audioshare.data.dto.call.CallSnapshot
import mme.corp.audioshare.data.repository.CallRepository
import mme.corp.audioshare.network.realtime.RealtimeSignalingClient
import java.util.UUID

/** Single source of truth for online call controls. Media keys and local host approvals are separate. */
class OnlineCallCoordinator(
    private val calls: CallRepository,
    private val signaling: RealtimeSignalingClient
) {
    data class State(
        val roomId: String? = null, val call: CallSnapshot? = null,
        val busy: Boolean = false, val error: String? = null
    )

    private val mutex = Mutex()
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var snapshotJob: Job? = null
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    suspend fun attach(roomId: String) {
        requireUuid(roomId)
        snapshotJob?.cancel()
        snapshotJob = observerScope.launch {
            signaling.snapshotsRequired.collect { request ->
                if (request.roomId == roomId ||
                    (request.callId != null && request.callId == _state.value.call?.id)
                ) {
                    // New authoritative REST snapshot. No replay assumption.
                    load(roomId)
                }
            }
        }
        signaling.subscribeRoom(roomId)
        signaling.connect()
        load(roomId)
    }

    suspend fun load(roomId: String): Result<CallSnapshot> = run(roomId, quietNotFound = true) {
        calls.current(roomId)
    }

    suspend fun start(roomId: String) = run(roomId) { calls.start(roomId) }
    suspend fun join(roomId: String, callId: String) = run(roomId) { calls.join(callId) }
    suspend fun leave(roomId: String, callId: String) = run(roomId) { calls.leave(callId) }
    suspend fun end(roomId: String, callId: String) = run(roomId) { calls.end(callId) }

    private suspend fun run(
        roomId: String, quietNotFound: Boolean = false,
        action: suspend () -> Result<CallSnapshot>
    ): Result<CallSnapshot> {
        requireUuid(roomId)
        return mutex.withLock {
            _state.value = State(roomId = roomId, busy = true)
            try {
                val result = action()
                result.onSuccess { snapshot ->
                    if (snapshot.roomId != roomId || !validUuid(snapshot.id)) {
                        _state.value = State(roomId = roomId, error = "Unexpected call response")
                        return@withLock Result.failure(IllegalStateException("Unexpected call response"))
                    }
                    _state.value = State(roomId = roomId, call = snapshot)
                    signaling.subscribeRoom(roomId)
                    if (snapshot.status != "ENDED") signaling.subscribeCall(snapshot.id)
                    else signaling.unsubscribeCall(snapshot.id)
                }.onFailure {
                    _state.value = State(
                        roomId = roomId,
                        error = if (quietNotFound) null else "Call request unsuccessful"
                    )
                }
                result
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                _state.value = State(roomId = roomId, error = "Call request unsuccessful")
                Result.failure(e)
            }
        }
    }

    fun detach() {
        snapshotJob?.cancel()
        snapshotJob = null
        state.value.call?.id?.let(signaling::unsubscribeCall)
        state.value.roomId?.let(signaling::unsubscribeRoom)
        signaling.close()
        _state.value = State()
    }

    private fun requireUuid(value: String) {
        require(validUuid(value)) { "Expected ServerRelay UUID" }
    }

    private fun validUuid(value: String) = runCatching {
        UUID.fromString(value).toString().equals(value, true)
    }.getOrDefault(false)
}
