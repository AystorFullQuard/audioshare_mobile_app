package mme.corp.audioshare.data.model.room

import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility

data class Room(
    val id: String,
    val ownerUserId: String,
    val ownerDeviceId: String,
    val name: String,
    val status: RoomStatus,
    val visibility: RoomVisibility,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?
)
