package mme.corp.audioshare.data.dto.room

import mme.corp.audioshare.data.dto.presence.PresenceState

data class RoomMemberResponse(
    val id: String,
    val roomId: String,
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarURL: String? = null,
    val role: RoomMemberRole,
    val state: RoomMemberState,
    val presenceState: PresenceState? = null,
    val presenceLastSeenAt: String? = null,
    val joinedAt: String,
    val membershipLastSeenAt: String,
    val leftAt: String? = null
)
