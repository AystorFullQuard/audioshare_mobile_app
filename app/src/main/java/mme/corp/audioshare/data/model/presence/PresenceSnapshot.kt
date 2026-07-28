package mme.corp.audioshare.data.model.presence

import mme.corp.audioshare.data.dto.presence.PresenceState

data class PresenceSnapshot(
    val userId: String,
    val deviceId: String,
    val state: PresenceState,
    val currentRoomId: String?,
    val lastSeenAt: String
)
