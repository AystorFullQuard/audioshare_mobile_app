package mme.corp.audioshare.ui.screens.rooms

import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.model.room.RoomMember
import mme.corp.audioshare.room.RoomSessionOperation
import mme.corp.audioshare.room.RoomSessionState

/**
 * Immutable screen state. Room lifecycle data remains owned by RoomSessionCoordinator;
 * only transient UI inputs and presentation state are stored alongside its snapshot.
 */
data class RoomsUiState(
    val session: RoomSessionState = RoomSessionState(),
    val roomNameInput: String = "",
    val createVisibility: RoomVisibility = RoomVisibility.PRIVATE,
    val joinRoomIdInput: String = "",
    val roomDetailsRoomId: String? = null,
    val roomExitPending: Boolean = false,
    val confirmation: RoomActionConfirmation? = null,
    val runningOperation: RoomsUiOperation? = null,
    val feedback: RoomsUiFeedback? = null,
    val sessionErrorMessage: String? = null
) {
    val rooms: List<Room>
        get() = session.rooms

    val currentRoom: Room?
        get() = session.currentRoom

    val roomDetails: Room?
        get() = roomDetailsRoomId?.let { expectedRoomId ->
            session.currentRoom?.takeIf { it.id == expectedRoomId }
        }

    val activeMembers: List<RoomMember>
        get() = session.activeMembers

    val isBusy: Boolean
        get() = roomExitPending || runningOperation != null || session.isBusy

    val isRoomTransitionRunning: Boolean
        get() = roomExitPending ||
            runningOperation.isRoomTransition() ||
            session.activeOperations.any(RoomSessionOperation::isRoomTransition)

    val isDestructiveOperationRunning: Boolean
        get() = runningOperation.isDestructiveTransition() ||
            session.activeOperations.any(RoomSessionOperation::isDestructiveTransition)

    val visibleErrorMessage: String?
        get() = feedback
            ?.takeIf(RoomsUiFeedback::isError)
            ?.message
            ?: sessionErrorMessage
}

data class RoomsUiFeedback(
    val message: String,
    val isError: Boolean
)

enum class RoomActionConfirmation {
    LEAVE,
    ARCHIVE
}

enum class RoomsUiOperation {
    LOAD_ROOMS,
    OPEN_ROOM,
    DEACTIVATE_ROOM,
    CREATE_ROOM,
    JOIN_ROOM,
    REFRESH_ROOM,
    LEAVE_ROOM,
    ARCHIVE_ROOM
}

sealed interface RoomsUiAction {
    data class RoomNameChanged(val value: String) : RoomsUiAction
    data class CreateVisibilityChanged(
        val value: RoomVisibility
    ) : RoomsUiAction
    data class JoinRoomIdChanged(val value: String) : RoomsUiAction

    data object LoadRooms : RoomsUiAction
    data class OpenRoom(val roomId: String) : RoomsUiAction
    data object DeactivateCurrentRoom : RoomsUiAction
    data class OpenRoomDetails(val roomId: String) : RoomsUiAction
    data class RefreshVisibleRoom(val roomId: String) : RoomsUiAction
    data object RetryRoomDetails : RoomsUiAction
    data object CreateRoom : RoomsUiAction
    data object JoinLocalDiscoveryRoom : RoomsUiAction
    data object RefreshCurrentRoom : RoomsUiAction
    data object RequestLeaveConfirmation : RoomsUiAction
    data object RequestArchiveConfirmation : RoomsUiAction
    data object DismissConfirmation : RoomsUiAction
    data object ConfirmRoomAction : RoomsUiAction
    data object LeaveCurrentRoom : RoomsUiAction
    data object ArchiveCurrentRoom : RoomsUiAction
    data object FeedbackConsumed : RoomsUiAction
}

sealed interface RoomsUiEvent {
    data class NavigateToRoom(val roomId: String) : RoomsUiEvent
    data object NavigateToRooms : RoomsUiEvent
}

private fun RoomsUiOperation?.isRoomTransition(): Boolean = when (this) {
    RoomsUiOperation.OPEN_ROOM,
    RoomsUiOperation.DEACTIVATE_ROOM,
    RoomsUiOperation.CREATE_ROOM,
    RoomsUiOperation.JOIN_ROOM,
    RoomsUiOperation.LEAVE_ROOM,
    RoomsUiOperation.ARCHIVE_ROOM -> true
    RoomsUiOperation.LOAD_ROOMS,
    RoomsUiOperation.REFRESH_ROOM,
    null -> false
}

private fun RoomsUiOperation?.isDestructiveTransition(): Boolean =
    this == RoomsUiOperation.LEAVE_ROOM || this == RoomsUiOperation.ARCHIVE_ROOM

private fun RoomSessionOperation.isRoomTransition(): Boolean = when (this) {
    RoomSessionOperation.OPEN_ROOM,
    RoomSessionOperation.ACTIVATE,
    RoomSessionOperation.DEACTIVATE,
    RoomSessionOperation.CREATE,
    RoomSessionOperation.JOIN,
    RoomSessionOperation.LEAVE,
    RoomSessionOperation.ARCHIVE -> true
    RoomSessionOperation.RESTORE,
    RoomSessionOperation.LOAD_ROOMS,
    RoomSessionOperation.REFRESH_ROOM,
    RoomSessionOperation.REFRESH_MEMBERS,
    RoomSessionOperation.RECONNECT -> false
}

private fun RoomSessionOperation.isDestructiveTransition(): Boolean =
    this == RoomSessionOperation.LEAVE || this == RoomSessionOperation.ARCHIVE
