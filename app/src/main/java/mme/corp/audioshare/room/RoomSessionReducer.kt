package mme.corp.audioshare.room

import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.model.room.RoomMember

/**
 * Pure room-state reducer shared by REST orchestration now and server events later.
 */
sealed interface RoomSessionMutation {

    data class BootstrapSnapshot(
        val rooms: List<Room>,
        val currentRoom: Room?
    ) : RoomSessionMutation

    data class RoomsReplaced(
        val rooms: List<Room>,
        val clearSelectionIfMissing: Boolean = true
    ) : RoomSessionMutation

    data class RoomSelected(
        val room: Room,
        val members: List<RoomMember>
    ) : RoomSessionMutation

    data class RoomActivated(
        val room: Room,
        val members: List<RoomMember>
    ) : RoomSessionMutation

    data class RoomDeactivated(
        val roomId: String
    ) : RoomSessionMutation

    data class RoomDetailsUpdated(
        val room: Room
    ) : RoomSessionMutation

    data class MembersUpdated(
        val roomId: String,
        val members: List<RoomMember>
    ) : RoomSessionMutation

    data class RoomRemoved(
        val roomId: String
    ) : RoomSessionMutation

    data object Cleared : RoomSessionMutation
}

object RoomSessionReducer {

    fun reduce(
        state: RoomSessionState,
        mutation: RoomSessionMutation
    ): RoomSessionState = when (mutation) {
        is RoomSessionMutation.BootstrapSnapshot -> reduceBootstrapSnapshot(
            state,
            mutation
        )

        is RoomSessionMutation.RoomsReplaced -> reduceRoomsReplaced(
            state,
            mutation
        )

        is RoomSessionMutation.RoomSelected -> reduceRoomSelected(
            state,
            mutation
        )

        is RoomSessionMutation.RoomActivated -> reduceRoomActivated(
            state,
            mutation
        )

        is RoomSessionMutation.RoomDeactivated -> reduceRoomDeactivated(
            state,
            mutation
        )

        is RoomSessionMutation.RoomDetailsUpdated -> reduceRoomDetailsUpdated(
            state,
            mutation
        )

        is RoomSessionMutation.MembersUpdated -> reduceMembersUpdated(
            state,
            mutation
        )

        is RoomSessionMutation.RoomRemoved -> reduceRoomRemoved(
            state,
            mutation
        )

        RoomSessionMutation.Cleared -> RoomSessionState()
    }

    private fun reduceBootstrapSnapshot(
        state: RoomSessionState,
        mutation: RoomSessionMutation.BootstrapSnapshot
    ): RoomSessionState = state.copy(
        rooms = mutation.rooms,
        currentRoom = mutation.currentRoom,
        activeMembers = emptyList()
    )

    private fun reduceRoomsReplaced(
        state: RoomSessionState,
        mutation: RoomSessionMutation.RoomsReplaced
    ): RoomSessionState {
        val selected = resolveSelectedRoom(state, mutation)
        val activeMembers = if (selected == null) {
            emptyList()
        } else {
            state.activeMembers
        }
        return state.copy(
            rooms = mutation.rooms,
            currentRoom = selected,
            activeMembers = activeMembers
        )
    }

    private fun resolveSelectedRoom(
        state: RoomSessionState,
        mutation: RoomSessionMutation.RoomsReplaced
    ): Room? {
        val current = state.currentRoom ?: return null
        val replacement = mutation.rooms.firstOrNull { it.id == current.id }

        return when {
            replacement != null -> replacement
            mutation.clearSelectionIfMissing -> null
            else -> current
        }
    }

    private fun reduceRoomSelected(
        state: RoomSessionState,
        mutation: RoomSessionMutation.RoomSelected
    ): RoomSessionState = selectRoom(
        state = state,
        room = mutation.room,
        members = mutation.members
    )

    private fun reduceRoomActivated(
        state: RoomSessionState,
        mutation: RoomSessionMutation.RoomActivated
    ): RoomSessionState = selectRoom(
        state = state,
        room = mutation.room,
        members = mutation.members
    )

    private fun selectRoom(
        state: RoomSessionState,
        room: Room,
        members: List<RoomMember>
    ): RoomSessionState = state.copy(
        rooms = state.rooms.upsert(room),
        currentRoom = room,
        activeMembers = members
    )

    private fun reduceRoomDeactivated(
        state: RoomSessionState,
        mutation: RoomSessionMutation.RoomDeactivated
    ): RoomSessionState = if (state.currentRoom?.id == mutation.roomId) {
        state.copy(
            currentRoom = null,
            activeMembers = emptyList()
        )
    } else {
        state
    }

    private fun reduceRoomDetailsUpdated(
        state: RoomSessionState,
        mutation: RoomSessionMutation.RoomDetailsUpdated
    ): RoomSessionState = state.copy(
        rooms = state.rooms.upsert(mutation.room),
        currentRoom = state.currentRoom.replaceIfSameRoom(mutation.room)
    )

    private fun reduceMembersUpdated(
        state: RoomSessionState,
        mutation: RoomSessionMutation.MembersUpdated
    ): RoomSessionState = if (state.currentRoom?.id == mutation.roomId) {
        state.copy(activeMembers = mutation.members)
    } else {
        state
    }

    private fun reduceRoomRemoved(
        state: RoomSessionState,
        mutation: RoomSessionMutation.RoomRemoved
    ): RoomSessionState {
        val removesSelection = state.currentRoom?.id == mutation.roomId
        val currentRoom = if (removesSelection) null else state.currentRoom
        val activeMembers = if (removesSelection) emptyList() else state.activeMembers
        return state.copy(
            rooms = state.rooms.filterNot { it.id == mutation.roomId },
            currentRoom = currentRoom,
            activeMembers = activeMembers
        )
    }

    private fun Room?.replaceIfSameRoom(replacement: Room): Room? =
        if (this?.id == replacement.id) replacement else this

    private fun List<Room>.upsert(room: Room): List<Room> {
        val index = indexOfFirst { it.id == room.id }
        if (index < 0) {
            return this + room
        }
        return toMutableList().apply { set(index, room) }
    }
}
