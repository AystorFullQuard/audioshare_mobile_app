package mme.corp.audioshare.backend.presence

import kotlinx.serialization.Serializable
import mme.corp.audioshare.backend.bootstrap.PresenceState

@Serializable
data class PresenceHeartbeatRequest(
    val deviceId: String,
    val state: PresenceState = PresenceState.ONLINE
)

@Serializable
data class PresenceHeartbeatResponse(
    val userId: String,
    val deviceId: String,
    val state: PresenceState,
    val currentRoomId: String? = null,
    val lastSeenAt: String
)
