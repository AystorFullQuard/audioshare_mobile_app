package mme.corp.audioshare.network.realtime

import com.google.gson.JsonParser
import java.util.UUID

/** Accept only bounded, versioned backend signaling envelopes. Never deserialize media here. */
data class ServerSignal(
    val type: String,
    val eventId: String,
    val roomId: String?,
    val callId: String?,
    val sequence: Long?
)

internal object RealtimeEnvelopeValidator {
    private val types = setOf(
        "SERVER_READY", "PING", "SNAPSHOT_REQUIRED", "ERROR", "SUBSCRIBED",
        "UNSUBSCRIBED", "CALL_STARTED", "PARTICIPANT_JOINED", "PARTICIPANT_LEFT",
        "CALL_ENDED", "ROOM_ARCHIVED", "MEMBERSHIP_UPDATED", "PRESENCE_UPDATED"
    )

    fun decode(text: String): ServerSignal? = runCatching {
        if (text.toByteArray(Charsets.UTF_8).size !in 2..16_384) return null
        val json = JsonParser.parseString(text).asJsonObject
        if (json.get("version")?.asString != "1") return null
        val type = json.get("type")?.asString ?: return null
        if (type !in types) return null
        val eventId = json.get("eventId")?.asString ?: return null
        if (!validUuid(eventId)) return null
        val roomId = json.get("roomId")?.takeUnless { it.isJsonNull }?.asString
        val callId = json.get("callId")?.takeUnless { it.isJsonNull }?.asString
        if (roomId != null && !validUuid(roomId)) return null
        if (callId != null && !validUuid(callId)) return null
        val sequence = json.get("sequence")?.takeUnless { it.isJsonNull }?.asLong
        if (sequence != null && sequence < 0L) return null
        ServerSignal(type, eventId, roomId, callId, sequence)
    }.getOrNull()

    private fun validUuid(id: String): Boolean = runCatching {
        UUID.fromString(id).toString().equals(id, ignoreCase = true)
    }.getOrDefault(false)
}
