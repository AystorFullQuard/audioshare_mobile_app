package mme.corp.audioshare.backend.rooms

import kotlinx.serialization.Serializable

@Serializable
data class CreateRoomRequest(
    val ownerDeviceId: String,
    val name: String? = null,
    val visibility: RoomVisibility = RoomVisibility.PRIVATE
)

@Serializable
data class RoomResponse(
    val id: String,
    val ownerUserId: String,
    val ownerDeviceId: String,
    val name: String? = null,
    val status: RoomStatus,
    val visibility: RoomVisibility,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String? = null
)

@Serializable
data class CreateRoomInviteRequest(
    val deviceId: String,
    val expiresInMinutes: Int? = null,
    val maxUses: Int? = null
)

@Serializable
data class RoomInviteResponse(
    val id: String,
    val roomId: String,
    val inviteCode: String,
    val maxUses: Int,
    val usedCount: Int,
    val expiresAt: String,
    val createdAt: String
)

@Serializable
data class JoinRoomByCodeRequest(
    val deviceId: String,
    val inviteCode: String
)

@Serializable
data class RoomDeviceActionRequest(
    val deviceId: String
)

@Serializable
data class RoomMemberResponse(
    val id: String,
    val roomId: String,
    val userId: String,
    val role: RoomMemberRole,
    val state: RoomMemberState,
    val joinedAt: String,
    val lastSeenAt: String? = null,
    val leftAt: String? = null
)

@Serializable
enum class RoomVisibility {
    PRIVATE,
    LOCAL_DISCOVERY
}

@Serializable
enum class RoomStatus {
    ACTIVE,
    ARCHIVED
}

@Serializable
enum class RoomMemberRole {
    OWNER,
    MEMBER
}

@Serializable
enum class RoomMemberState {
    ACTIVE,
    LEFT
}
