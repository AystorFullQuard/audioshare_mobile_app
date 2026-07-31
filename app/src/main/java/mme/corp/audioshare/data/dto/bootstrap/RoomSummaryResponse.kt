package mme.corp.audioshare.data.dto.bootstrap

import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility

data class RoomSummaryResponse(
    val id: String,
    val ownerUserId: String,
    val ownerDeviceId: String,
    val name: String?,
    val status: RoomStatus,
    val visibility: RoomVisibility,
    val currentUserRole: RoomMemberRole,
    val activeMemberCount: Long,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String? = null
)
