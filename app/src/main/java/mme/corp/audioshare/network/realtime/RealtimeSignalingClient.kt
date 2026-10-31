package mme.corp.audioshare.network.realtime

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mme.corp.audioshare.runtime.RuntimeWorkBudget
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.data.storage.TokenStore
import mme.corp.audioshare.network.retrofit.NetworkConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Authenticated ServerRelay V1 signaling only. Media is never transported through this socket. */
class RealtimeSignalingClient(
    private val tokens: TokenStore,
    private val devices: DeviceIdStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .pingInterval(25, TimeUnit.SECONDS)
        .build(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }
    data class SnapshotRequest(val roomId: String?, val callId: String?)

    private val gson = Gson()
    private val retryBudget = RuntimeWorkBudget(
        foregroundPollingMillis = 1_000,
        backgroundPollingMillis = 1_000, maxBackoffMillis = 30_000
    )
    private val lock = Any()
    private var socket: WebSocket? = null
    private var generation = 0L
    private var closedByClient = true
    private var ready = false
    private var attempts = 0
    private var retryJob: Job? = null
    private val desiredRooms = linkedSetOf<String>()
    private val desiredCalls = linkedSetOf<String>()
    private val seenEventIds = LinkedHashSet<String>()
    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state = _state.asStateFlow()
    val inbound = MutableSharedFlow<String>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = MutableSharedFlow<ServerSignal>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val snapshotsRequired = MutableSharedFlow<SnapshotRequest>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** The returned success means a handshake was initiated; CONNECTED is emitted after SERVER_READY. */
    suspend fun connect(): Result<Unit> {
        synchronized(lock) {
            if (!closedByClient && _state.value != State.DISCONNECTED) return Result.success(Unit)
            closedByClient = false
            retryJob?.cancel()
            retryJob = null
            attempts = 0
        }
        return openSocket(reconnecting = false)
    }

    private suspend fun openSocket(reconnecting: Boolean): Result<Unit> {
        val token = tokens.getTokenSnapshot()?.accessToken?.takeIf { it.isNotBlank() }
            ?: return terminalFailure("Sign in before connecting realtime")
        val deviceId = devices.getDeviceId()?.takeIf(::isUuid)
            ?: return terminalFailure("Registered device UUID is required")
        val baseUrl = NetworkConfig.BASE_URL.toHttpUrl()
        // OkHttp's WebSocket upgrade uses an HTTP(S) URL; it does not accept wss in HttpUrl.Builder.
        // HTTPS becomes WSS on the wire via the WebSocket upgrade handshake.
        val url = baseUrl.newBuilder().encodedPath("/ws/realtime").build()
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("X-AudioShare-Device-Id", deviceId)
            .build()
        synchronized(lock) {
            if (closedByClient) return Result.failure(IllegalStateException("Realtime is closed"))
            generation++
            val current = generation
            ready = false
            _state.value = if (reconnecting) State.RECONNECTING else State.CONNECTING
            socket?.cancel()
            socket = client.newWebSocket(request, listener(current))
        }
        return Result.success(Unit)
    }

    private fun terminalFailure(message: String): Result<Unit> {
        synchronized(lock) { _state.value = State.DISCONNECTED }
        return Result.failure(IllegalStateException(message))
    }

    private fun listener(owner: Long) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (owner == generation && !closedByClient) webSocket.send(envelope("CLIENT_READY"))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = RealtimeEnvelopeValidator.decode(text) ?: run {
                webSocket.close(1008, "Invalid protocol envelope")
                return
            }
            synchronized(lock) {
                if (owner != generation || closedByClient) return
                if (!seenEventIds.add(message.eventId)) return
                if (seenEventIds.size > MAX_SEEN_EVENTS) {
                    seenEventIds.remove(seenEventIds.first())
                }
                if (message.type == "SERVER_READY" && !ready) {
                    ready = true
                    attempts = 0
                    _state.value = State.CONNECTED
                    // Server V1 does not replay events. Every scope gets a new snapshot request.
                    desiredRooms.forEach { id ->
                        webSocket.send(
                            envelope(
                                "SUBSCRIBE_ROOM",
                                roomId = id
                            )
                        )
                    }
                    desiredCalls.forEach { id ->
                        webSocket.send(
                            envelope(
                                "SUBSCRIBE_CALL",
                                callId = id
                            )
                        )
                    }
                    // V1 does not replay missed events. Refresh REST snapshots after every handshake,
                    // including the initial subscription and every reconnect.
                    desiredRooms.forEach { id ->
                        snapshotsRequired.tryEmit(
                            SnapshotRequest(
                                id,
                                null
                            )
                        )
                    }
                    desiredCalls.forEach { id ->
                        snapshotsRequired.tryEmit(
                            SnapshotRequest(
                                null,
                                id
                            )
                        )
                    }
                }
                if (message.type == "PING") webSocket.send(envelope("PONG"))
                if (message.type == "SNAPSHOT_REQUIRED") {
                    snapshotsRequired.tryEmit(SnapshotRequest(message.roomId, message.callId))
                }
                events.tryEmit(message)
                inbound.tryEmit(text)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            disconnected(owner, retry = response?.code !in setOf(400, 401, 403, 429))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            disconnected(owner, retry = code !in setOf(1007, 1008, 4004, 1009))
        }
    }

    private fun disconnected(owner: Long, retry: Boolean) {
        synchronized(lock) {
            if (owner != generation || closedByClient) return
            socket = null
            ready = false
            _state.value = State.DISCONNECTED
            if (!retry || attempts >= MAX_RETRIES) return
            val retryDelay = retryBudget.nextDelay(attempts, inForeground = true)
            attempts++
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(retryDelay)
                openSocket(reconnecting = true)
            }
        }
    }

    fun subscribeRoom(roomId: String): Boolean =
        changeScope(roomId, isCall = false, subscribe = true)

    fun unsubscribeRoom(roomId: String): Boolean =
        changeScope(roomId, isCall = false, subscribe = false)

    fun subscribeCall(callId: String): Boolean =
        changeScope(callId, isCall = true, subscribe = true)

    fun unsubscribeCall(callId: String): Boolean =
        changeScope(callId, isCall = true, subscribe = false)

    private fun changeScope(id: String, isCall: Boolean, subscribe: Boolean): Boolean {
        if (!isUuid(id)) return false
        return synchronized(lock) {
            val set = if (isCall) desiredCalls else desiredRooms
            if (subscribe && desiredCalls.size + desiredRooms.size >= MAX_SUBSCRIPTIONS && id !in set) return@synchronized false
            if (subscribe) set.add(id) else set.remove(id)
            if (ready) {
                if (subscribe) snapshotsRequired.tryEmit(
                    SnapshotRequest(
                        if (isCall) null else id, if (isCall) id else null
                    )
                )
                socket?.send(
                    envelope(
                        if (subscribe) if (isCall) "SUBSCRIBE_CALL" else "SUBSCRIBE_ROOM"
                        else if (isCall) "UNSUBSCRIBE_CALL" else "UNSUBSCRIBE_ROOM",
                        roomId = if (isCall) null else id,
                        callId = if (isCall) id else null
                    )
                ) ?: false
            } else true
        }
    }

    fun close() {
        synchronized(lock) {
            closedByClient = true
            generation++
            retryJob?.cancel()
            retryJob = null
            socket?.close(1000, "Client closed")
            socket = null
            ready = false
            attempts = 0
            desiredRooms.clear()
            desiredCalls.clear()
            seenEventIds.clear()
            _state.value = State.DISCONNECTED
        }
    }

    private fun envelope(type: String, roomId: String? = null, callId: String? = null): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        return gson.toJson(JsonObject().apply {
            addProperty("type", type)
            addProperty("version", "1")
            addProperty("eventId", UUID.randomUUID().toString())
            addProperty("timestamp", timestamp)
            if (roomId != null) addProperty("roomId", roomId)
            if (callId != null) addProperty("callId", callId)
            add("payload", JsonObject())
        })
    }

    private fun isUuid(id: String) =
        runCatching { UUID.fromString(id).toString().equals(id, true) }.getOrDefault(false)

    private companion object {
        const val MAX_RETRIES = 5
        const val MAX_SUBSCRIPTIONS = 32
        const val MAX_SEEN_EVENTS = 256
    }
}
