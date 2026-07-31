package mme.corp.audioshare.data.dto.room

data class RoomResponse(
    val id: String,
    val ownerUserId: String,
    val ownerDeviceId: String,
    val name: String,
    val status: RoomStatus,
    val visibility: RoomVisibility,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String? = null
)
