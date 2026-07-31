package mme.corp.audioshare.data.dto.room

data class CreateRoomRequest(
    val ownerDeviceId: String,
    val name: String? = null,
    val visibility: RoomVisibility = RoomVisibility.PRIVATE
)
