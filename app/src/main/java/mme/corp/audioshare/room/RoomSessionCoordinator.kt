package mme.corp.audioshare.room

import kotlinx.coroutines.flow.StateFlow
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room

fun interface RoomSessionBootstrapRestorer {
    suspend fun restoreFromBootstrap(
        bootstrap: SessionBootstrapResponse
    ): Result<RoomSessionState>
}

interface RoomSessionRuntimeController {
    fun markDisconnected()
    fun clearForLogout()
    fun resetAfterStartupFailure()
    suspend fun reconnect(): Result<RoomSessionState>

    companion object {
        val NO_OP: RoomSessionRuntimeController =
            object : RoomSessionRuntimeController {
                override fun markDisconnected() = Unit
                override fun clearForLogout() = Unit
                override fun resetAfterStartupFailure() = Unit
                override suspend fun reconnect(): Result<RoomSessionState> =
                    Result.success(RoomSessionState())
            }
    }
}

interface RoomSessionCoordinator :
    RoomSessionBootstrapRestorer,
    RoomSessionRuntimeController {

    val state: StateFlow<RoomSessionState>

    suspend fun loadRooms(): Result<List<Room>>

    suspend fun openRoom(roomId: String): Result<RoomSessionState>

    suspend fun activateRoom(roomId: String): Result<RoomSessionState>

    suspend fun deactivateCurrentRoom(): Result<RoomSessionState>

    suspend fun refreshCurrentRoom(): Result<RoomSessionState>

    suspend fun refreshActiveMembers(): Result<RoomSessionState>

    suspend fun createRoom(
        name: String? = null,
        visibility: RoomVisibility = RoomVisibility.PRIVATE
    ): Result<RoomSessionState>

    suspend fun joinLocalDiscoveryRoom(roomId: String): Result<RoomSessionState>

    suspend fun leaveCurrentRoom(): Result<RoomSessionState>

    suspend fun archiveCurrentRoom(): Result<RoomSessionState>
}
