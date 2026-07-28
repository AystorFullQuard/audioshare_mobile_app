package mme.corp.audioshare.data.dto.presence

/**
 * A null state means "keep the current server-authoritative state".
 */
data class PresenceHeartbeatRequest(
    val deviceId: String,
    val state: PresenceState? = null
)
