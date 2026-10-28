package mme.corp.audioshare.data.dto.presence

/** Heartbeat uses the session-bound device; activity is managed by the backend. */
data class PresenceHeartbeatRequest(val deviceId: String)
