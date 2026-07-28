package mme.corp.audioshare.data.dto.presence

data class PresenceHeartbeatResponse(
    val userId: String,
    val deviceId: String,
    val state: PresenceState,
    val currentRoomId: String? = null,
    val lastSeenAt: String
)
