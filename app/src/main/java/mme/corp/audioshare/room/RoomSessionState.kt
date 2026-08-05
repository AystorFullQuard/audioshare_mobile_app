package mme.corp.audioshare.room

import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.model.room.RoomMember

enum class RoomSessionOperation {
    RESTORE,
    LOAD_ROOMS,
    OPEN_ROOM,
    ACTIVATE,
    DEACTIVATE,
    REFRESH_ROOM,
    REFRESH_MEMBERS,
    CREATE,
    JOIN,
    LEAVE,
    ARCHIVE,
    RECONNECT
}

data class RoomSessionError(
    val operation: RoomSessionOperation,
    val roomId: String? = null,
    val type: String,
    val httpCode: Int? = null,
    val apiCode: String? = null,
    val retryable: Boolean = false
)

data class RoomSessionState(
    val rooms: List<Room> = emptyList(),
    val currentRoom: Room? = null,
    val activeMembers: List<RoomMember> = emptyList(),
    val activeOperations: Set<RoomSessionOperation> = emptySet(),
    val isConnected: Boolean = true,
    val isStale: Boolean = false,
    val lastError: RoomSessionError? = null
) {
    val isBusy: Boolean
        get() = activeOperations.isNotEmpty()
}
