package mme.corp.audioshare.network.realtime

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
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

/** Versioned ServerRelay JSON signaling, never an audio transport. Connection is opt-in. */
class RealtimeSignalingClient(
    private val tokens: TokenStore,
    private val devices: DeviceIdStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    private var socket: WebSocket? = null
    private val lock = Any()
    val inbound = MutableSharedFlow<String>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    suspend fun connect(): Result<Unit> {
        val token = tokens.getTokenSnapshot()?.accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Sign in before connecting realtime"))
        val deviceId = devices.getDeviceId()?.takeIf { isUuid(it) }
            ?: return Result.failure(IllegalStateException("Registered device UUID is required"))
        val httpUrl = NetworkConfig.BASE_URL.toHttpUrl()
        val socketUrl = httpUrl.newBuilder()
            .scheme(if (httpUrl.isHttps) "wss" else "ws")
            .encodedPath("/ws/realtime")
            .build()
        val request = Request.Builder().url(socketUrl)
            .header("Authorization", "Bearer $token")
            .header("X-AudioShare-Device-Id", deviceId)
            .build()
        synchronized(lock) {
            socket?.close(1000, null)
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(envelope("CLIENT_READY"))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE_BYTES) inbound.tryEmit(
                        text
                    )
                    else webSocket.close(1009, "Message too large")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    synchronized(lock) { if (socket === webSocket) socket = null }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    synchronized(lock) { if (socket === webSocket) socket = null }
                }
            })
        }
        return Result.success(Unit) // handshake acceptance is asynchronous
    }

    fun subscribeRoom(roomId: String): Boolean = sendScoped("SUBSCRIBE_ROOM", roomId, false)
    fun unsubscribeRoom(roomId: String): Boolean = sendScoped("UNSUBSCRIBE_ROOM", roomId, false)
    fun subscribeCall(callId: String): Boolean = sendScoped("SUBSCRIBE_CALL", callId, true)
    fun unsubscribeCall(callId: String): Boolean = sendScoped("UNSUBSCRIBE_CALL", callId, true)

    private fun sendScoped(type: String, id: String, call: Boolean): Boolean {
        if (!isUuid(id)) return false
        return synchronized(lock) {
            socket?.send(
                envelope(
                    type,
                    if (call) null else id,
                    if (call) id else null
                )
            ) ?: false
        }
    }

    fun close() {
        synchronized(lock) { socket?.close(1000, "Client closed"); socket = null }
    }

    private fun envelope(type: String, roomId: String? = null, callId: String? = null): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val objectNode = JsonObject().apply {
            addProperty("type", type)
            addProperty("version", "1")
            addProperty("eventId", UUID.randomUUID().toString())
            addProperty("timestamp", timestamp)
            if (roomId != null) addProperty("roomId", roomId)
            if (callId != null) addProperty("callId", callId)
            add("payload", JsonObject())
        }
        return gson.toJson(objectNode)
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private companion object {
        const val MAX_MESSAGE_BYTES = 16_384
    }
}
