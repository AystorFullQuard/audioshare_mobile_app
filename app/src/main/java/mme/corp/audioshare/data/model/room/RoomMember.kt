package mme.corp.audioshare.data.model.room

import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomMemberState

data class RoomMember(
    val membershipId: String,
    val roomId: String,
    val userId: String,
    val username: String?,
    val displayName: String?,
    val avatarURL: String?,
    val role: RoomMemberRole,
    val membershipState: RoomMemberState,
    val presenceState: PresenceState?,
    val presenceLastSeenAt: String?,
    val joinedAt: String,
    val membershipLastSeenAt: String,
    val leftAt: String?
)
