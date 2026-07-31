package mme.corp.audioshare.data.dto.bootstrap

import mme.corp.audioshare.data.dto.presence.PresenceState

data class SessionPresenceResponse(
    val userId: String,
    val state: PresenceState,
    val currentRoomId: String? = null,
    val lastSeenAt: String,
    val updatedAt: String? = null
)
