package mme.corp.audioshare.ui.screens.rooms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.exception.DeviceBootstrapRequiredException
import mme.corp.audioshare.room.RoomSessionCoordinator
import mme.corp.audioshare.room.RoomSessionError
import mme.corp.audioshare.room.RoomSessionOperation
import mme.corp.audioshare.room.RoomSessionState
import java.io.IOException

private const val ROOM_ID_REQUIRED_MESSAGE = "Room ID is required."
private const val ROOM_UNAVAILABLE_MESSAGE = "Room is no longer available."
private const val ROOM_OPERATION_FAILED_MESSAGE = "Room operation failed."
private const val ROOM_CLOSE_FAILED_MESSAGE = "Unable to close the room."
private const val ROOM_CREATED_MESSAGE = "Room created. Use Open to enter it."
private const val ROOM_JOINED_MESSAGE = "Room joined. Use Open to enter it."
private const val ROOM_CREATE_INCONSISTENT_MESSAGE =
    "Room creation returned an inconsistent state."
private const val ROOM_JOIN_INCONSISTENT_MESSAGE =
    "Room join returned an inconsistent state."
private const val MEMBER_ACTION_REQUIRED_MESSAGE =
    "Only a room member can leave this room."
private const val OWNER_ACTION_REQUIRED_MESSAGE =
    "Only the room owner can archive this room."
private const val ROOM_NAME_STATE_KEY = "rooms.roomName"
private const val CREATE_VISIBILITY_STATE_KEY = "rooms.createVisibility"
private const val JOIN_ROOM_ID_STATE_KEY = "rooms.joinRoomId"
private const val OPERATION_IN_PROGRESS_ERROR_TYPE =
    "RoomOperationInProgressException"
private const val ROOM_NAME_MAX_LENGTH = 80
private const val ROOM_ID_INPUT_MAX_LENGTH = 128

class RoomsViewModel(
    private val coordinator: RoomSessionCoordinator,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(
        RoomsUiState(
            session = coordinator.state.value,
            roomNameInput = savedStateHandle
                .get<String>(ROOM_NAME_STATE_KEY)
                .orEmpty()
                .take(ROOM_NAME_MAX_LENGTH),
            createVisibility = savedStateHandle
                .get<String>(CREATE_VISIBILITY_STATE_KEY)
                .toRoomVisibility(),
            joinRoomIdInput = savedStateHandle
                .get<String>(JOIN_ROOM_ID_STATE_KEY)
                .orEmpty()
                .take(ROOM_ID_INPUT_MAX_LENGTH),
            sessionErrorMessage = coordinator.state.value.lastError
                .toDisplayMessageOrNull()
        )
    )
    val uiState: StateFlow<RoomsUiState> = mutableUiState.asStateFlow()

    private val eventsChannel = Channel<RoomsUiEvent>(Channel.BUFFERED)
    val events: Flow<RoomsUiEvent> = eventsChannel.receiveAsFlow()

    private var actionJob: Job? = null
    private var roomDetailsWasAvailable = false
    private var roomDetailsExitEventSent = false
    private var roomDetailsLoadAttemptedForId: String? = null

    init {
        viewModelScope.launch {
            coordinator.state.collect(::applySessionState)
        }
    }

    fun onAction(action: RoomsUiAction) {
        when (action) {
            is RoomsUiAction.RoomNameChanged -> updateRoomName(action.value)
            is RoomsUiAction.CreateVisibilityChanged ->
                updateCreateVisibility(action.value)
            is RoomsUiAction.JoinRoomIdChanged -> updateJoinRoomId(action.value)
            RoomsUiAction.LoadRooms -> loadRooms()
            is RoomsUiAction.OpenRoom -> openRoom(action.roomId)
            RoomsUiAction.DeactivateCurrentRoom -> deactivateCurrentRoom()
            is RoomsUiAction.OpenRoomDetails ->
                openRoomDetails(action.roomId, force = false)
            RoomsUiAction.RetryRoomDetails -> retryRoomDetails()
            RoomsUiAction.CreateRoom -> createRoom()
            RoomsUiAction.JoinLocalDiscoveryRoom -> joinLocalDiscoveryRoom()
            RoomsUiAction.RefreshCurrentRoom -> refreshCurrentRoom()
            RoomsUiAction.RequestLeaveConfirmation ->
                requestConfirmation(RoomActionConfirmation.LEAVE)
            RoomsUiAction.RequestArchiveConfirmation ->
                requestConfirmation(RoomActionConfirmation.ARCHIVE)
            RoomsUiAction.DismissConfirmation -> dismissConfirmation()
            RoomsUiAction.ConfirmRoomAction -> confirmRoomAction()
            RoomsUiAction.LeaveCurrentRoom -> leaveCurrentRoom()
            RoomsUiAction.ArchiveCurrentRoom -> archiveCurrentRoom()
            RoomsUiAction.FeedbackConsumed -> clearFeedback()
        }
    }

    private fun updateRoomName(value: String) {
        val boundedValue = value.take(ROOM_NAME_MAX_LENGTH)
        savedStateHandle[ROOM_NAME_STATE_KEY] = boundedValue
        mutableUiState.update { current ->
            current.copy(roomNameInput = boundedValue, feedback = null)
        }
    }

    private fun updateCreateVisibility(
        visibility: RoomVisibility
    ) {
        savedStateHandle[CREATE_VISIBILITY_STATE_KEY] = visibility.name
        mutableUiState.update { current ->
            current.copy(createVisibility = visibility, feedback = null)
        }
    }

    private fun updateJoinRoomId(value: String) {
        val boundedValue = value.take(ROOM_ID_INPUT_MAX_LENGTH)
        savedStateHandle[JOIN_ROOM_ID_STATE_KEY] = boundedValue
        mutableUiState.update { current ->
            current.copy(joinRoomIdInput = boundedValue, feedback = null)
        }
    }

    private fun loadRooms() {
        launchAction(RoomsUiOperation.LOAD_ROOMS) {
            coordinator.loadRooms()
                .onSuccess { showFeedback("Rooms refreshed.") }
                .onFailure { exception ->
                    showFailure(exception, RoomSessionOperation.LOAD_ROOMS)
                }
        }
    }

    private fun createRoom() {
        val submittedNameInput = mutableUiState.value.roomNameInput
        val submittedVisibility = mutableUiState.value.createVisibility
        val previousRoomIds = mutableUiState.value.rooms.map { it.id }.toSet()
        val expectedCurrentRoomId = mutableUiState.value.currentRoom?.id

        launchAction(RoomsUiOperation.CREATE_ROOM) {
            coordinator.createRoom(
                name = submittedNameInput.normalizedRoomName(),
                visibility = submittedVisibility
            ).onSuccess { state ->
                val createdMembershipAdded = state.rooms.any { room ->
                    room.id !in previousRoomIds &&
                        room.status == RoomStatus.ACTIVE &&
                        room.currentUserRole == RoomMemberRole.OWNER
                }
                val activeRoomUnchanged =
                    state.currentRoom?.id == expectedCurrentRoomId
                if (!createdMembershipAdded || !activeRoomUnchanged) {
                    showSessionFailureOrFallback(
                        state.lastError.forOperation(
                            RoomSessionOperation.CREATE,
                            null
                        ),
                        ROOM_CREATE_INCONSISTENT_MESSAGE
                    )
                } else {
                    clearRoomNameIfUnchanged(submittedNameInput)
                    showFeedback(ROOM_CREATED_MESSAGE)
                }
            }.onFailure { exception ->
                showFailure(exception, RoomSessionOperation.CREATE)
            }
        }
    }

    private fun joinLocalDiscoveryRoom() {
        if (isActionBlocked()) {
            return
        }

        val submittedRoomIdInput = mutableUiState.value.joinRoomIdInput
        val expectedCurrentRoomId = mutableUiState.value.currentRoom?.id
        val roomId = submittedRoomIdInput.normalizedRoomId()
        if (roomId == null) {
            showFeedback(ROOM_ID_REQUIRED_MESSAGE, isError = true)
            return
        }

        launchAction(RoomsUiOperation.JOIN_ROOM) {
            coordinator.joinLocalDiscoveryRoom(roomId)
                .onSuccess { state ->
                    val joinedRequestedRoom = state.rooms.any { room ->
                        room.id == roomId &&
                            room.status == RoomStatus.ACTIVE &&
                            room.currentUserRole != null
                    }
                    val activeRoomUnchanged =
                        state.currentRoom?.id == expectedCurrentRoomId
                    if (!joinedRequestedRoom || !activeRoomUnchanged) {
                        showSessionFailureOrFallback(
                            state.lastError.forOperation(
                                RoomSessionOperation.JOIN,
                                roomId
                            ),
                            ROOM_JOIN_INCONSISTENT_MESSAGE
                        )
                    } else {
                        clearJoinRoomIdIfUnchanged(submittedRoomIdInput)
                        showFeedback(ROOM_JOINED_MESSAGE)
                    }
                }
                .onFailure { exception ->
                    showFailure(
                        exception,
                        RoomSessionOperation.JOIN,
                        roomId
                    )
                }
        }
    }

    private fun openRoom(rawRoomId: String) {
        if (isActionBlocked()) {
            return
        }

        val roomId = rawRoomId.normalizedRoomId()
        if (roomId == null) {
            showFeedback(ROOM_ID_REQUIRED_MESSAGE, isError = true)
            emitEvent(RoomsUiEvent.NavigateToRooms)
            return
        }

        launchAction(RoomsUiOperation.OPEN_ROOM) {
            coordinator.openRoom(roomId)
                .onSuccess { state -> handleOpenRoomSuccess(roomId, state) }
                .onFailure { exception ->
                    showFailure(
                        exception,
                        RoomSessionOperation.OPEN_ROOM,
                        roomId
                    )
                }
        }
    }

    private fun deactivateCurrentRoom() {
        val expectedRoomId = currentRoomActionId()

        launchAction(RoomsUiOperation.DEACTIVATE_ROOM) {
            coordinator.deactivateCurrentRoom()
                .onSuccess { state ->
                    if (state.currentRoom == null) {
                        navigateAfterRoomLifecycleSuccess()
                    } else {
                        showSessionFailureOrFallback(
                            state.lastError.forOperation(
                                RoomSessionOperation.DEACTIVATE,
                                expectedRoomId
                            ),
                            ROOM_CLOSE_FAILED_MESSAGE
                        )
                    }
                }
                .onFailure { exception ->
                    showFailure(
                        exception,
                        RoomSessionOperation.DEACTIVATE,
                        expectedRoomId
                    )
                }
        }
    }

    private fun openRoomDetails(rawRoomId: String, force: Boolean) {
        val roomId = rawRoomId.normalizedRoomId()
        if (roomId == null) {
            showFeedback(ROOM_ID_REQUIRED_MESSAGE, isError = true)
            navigateFromRoomDetailsOnce()
            return
        }

        if (!force && canUseCurrentRoomSnapshot(roomId)) {
            prepareRoomDetailsTarget(roomId)
            roomDetailsWasAvailable = true
            roomDetailsLoadAttemptedForId = roomId
            return
        }
        if (!force &&
            mutableUiState.value.roomDetailsRoomId == roomId &&
            roomDetailsLoadAttemptedForId == roomId
        ) {
            return
        }

        launchAction(
            operation = RoomsUiOperation.OPEN_ROOM,
            onAccepted = {
                prepareRoomDetailsTarget(roomId)
                roomDetailsLoadAttemptedForId = roomId
            }
        ) {
            coordinator.openRoom(roomId)
                .onSuccess { state -> handleRoomDetailsOpenSuccess(roomId, state) }
                .onFailure { exception ->
                    handleRoomDetailsOpenFailure(roomId, exception)
                }
        }
    }

    private fun prepareRoomDetailsTarget(roomId: String) {
        val currentState = mutableUiState.value
        if (currentState.roomDetailsRoomId == roomId &&
            !currentState.roomExitPending
        ) {
            return
        }

        roomDetailsWasAvailable = false
        roomDetailsExitEventSent = false
        roomDetailsLoadAttemptedForId = null
        mutableUiState.update { current ->
            current.copy(
                roomDetailsRoomId = roomId,
                roomExitPending = false,
                confirmation = null,
                feedback = null
            )
        }
    }

    private fun canUseCurrentRoomSnapshot(roomId: String): Boolean {
        val session = coordinator.state.value
        return !session.isStale && session.currentRoom?.let { room ->
            room.id == roomId && room.status == RoomStatus.ACTIVE
        } == true
    }

    private fun handleRoomDetailsOpenSuccess(
        roomId: String,
        state: RoomSessionState
    ) {
        val opened = state.currentRoom?.let { room ->
            room.id == roomId && room.status == RoomStatus.ACTIVE
        } == true
        if (opened) {
            roomDetailsWasAvailable = true
            return
        }

        showSessionFailureOrFallback(
            state.lastError.forOperation(RoomSessionOperation.OPEN_ROOM, roomId),
            ROOM_UNAVAILABLE_MESSAGE
        )
        navigateFromRoomDetailsOnce()
    }

    private fun handleRoomDetailsOpenFailure(
        roomId: String,
        exception: Throwable
    ) {
        val sessionError = coordinator.state.value.lastError
            ?.takeIf { error ->
                error.matches(RoomSessionOperation.OPEN_ROOM, roomId)
            }
        if (sessionError?.type == OPERATION_IN_PROGRESS_ERROR_TYPE) {
            roomDetailsLoadAttemptedForId = null
            return
        }

        showFailure(exception, RoomSessionOperation.OPEN_ROOM, roomId)
        if (sessionError.indicatesUnavailableRoom()) {
            navigateFromRoomDetailsOnce()
        }
    }

    private fun retryRoomDetails() {
        val roomId = mutableUiState.value.roomDetailsRoomId ?: return
        openRoomDetails(roomId, force = true)
    }

    private fun handleOpenRoomSuccess(
        requestedRoomId: String,
        state: RoomSessionState
    ) {
        if (state.currentRoom?.let { room ->
                room.id == requestedRoomId && room.status == RoomStatus.ACTIVE
            } == true
        ) {
            emitEvent(RoomsUiEvent.NavigateToRoom(requestedRoomId))
            return
        }

        showSessionFailureOrFallback(
            error = state.lastError.forOperation(
                RoomSessionOperation.OPEN_ROOM,
                requestedRoomId
            ),
            fallback = ROOM_UNAVAILABLE_MESSAGE
        )
        emitEvent(RoomsUiEvent.NavigateToRooms)
    }

    private fun refreshCurrentRoom() {
        val expectedRoomId = currentRoomActionId()
        launchAction(RoomsUiOperation.REFRESH_ROOM) {
            val roomState = coordinator.refreshCurrentRoom()
                .getOrElse { exception ->
                    showFailure(
                        exception,
                        RoomSessionOperation.REFRESH_ROOM,
                        expectedRoomId
                    )
                    return@launchAction
                }

            if (!handleCurrentRoomAvailability(
                    roomState,
                    RoomSessionOperation.REFRESH_ROOM,
                    expectedRoomId
                )
            ) {
                return@launchAction
            }

            val membersState = coordinator.refreshActiveMembers()
                .getOrElse { exception ->
                    showFailure(
                        exception,
                        RoomSessionOperation.REFRESH_MEMBERS,
                        expectedRoomId
                    )
                    return@launchAction
                }

            if (handleCurrentRoomAvailability(
                    membersState,
                    RoomSessionOperation.REFRESH_MEMBERS,
                    expectedRoomId
                )
            ) {
                showFeedback("Room refreshed.")
            }
        }
    }

    private fun handleCurrentRoomAvailability(
        state: RoomSessionState,
        operation: RoomSessionOperation,
        expectedRoomId: String?
    ): Boolean {
        val currentRoomMatches = if (expectedRoomId == null) {
            state.currentRoom != null
        } else {
            state.currentRoom?.let { room ->
                room.id == expectedRoomId && room.status == RoomStatus.ACTIVE
            } == true
        }
        if (currentRoomMatches) return true

        showSessionFailureOrFallback(
            error = state.lastError.forOperation(operation, expectedRoomId),
            fallback = ROOM_UNAVAILABLE_MESSAGE
        )
        if (expectedRoomId == null) {
            emitEvent(RoomsUiEvent.NavigateToRooms)
        } else {
            navigateFromRoomDetailsOnce()
        }
        return false
    }

    private fun requestConfirmation(confirmation: RoomActionConfirmation) {
        if (isActionBlocked()) return

        val role = mutableUiState.value.roomDetails?.currentUserRole
        val allowed = when (confirmation) {
            RoomActionConfirmation.LEAVE -> role == RoomMemberRole.MEMBER
            RoomActionConfirmation.ARCHIVE -> role == RoomMemberRole.OWNER
        }
        if (!allowed) {
            showFeedback(
                message = when (confirmation) {
                    RoomActionConfirmation.LEAVE -> MEMBER_ACTION_REQUIRED_MESSAGE
                    RoomActionConfirmation.ARCHIVE -> OWNER_ACTION_REQUIRED_MESSAGE
                },
                isError = true
            )
            return
        }

        mutableUiState.update { current ->
            current.copy(confirmation = confirmation, feedback = null)
        }
    }

    private fun dismissConfirmation() {
        if (mutableUiState.value.isBusy) return
        mutableUiState.update { current -> current.copy(confirmation = null) }
    }

    private fun confirmRoomAction() {
        if (isActionBlocked()) return
        when (mutableUiState.value.confirmation) {
            RoomActionConfirmation.LEAVE -> leaveCurrentRoom()
            RoomActionConfirmation.ARCHIVE -> archiveCurrentRoom()
            null -> Unit
        }
    }

    private fun leaveCurrentRoom() {
        if (roomLifecycleActionRole() != RoomMemberRole.MEMBER) {
            showFeedback(MEMBER_ACTION_REQUIRED_MESSAGE, isError = true)
            return
        }

        val expectedRoomId = currentRoomActionId()
        launchAction(RoomsUiOperation.LEAVE_ROOM) {
            coordinator.leaveCurrentRoom()
                .onSuccess { state ->
                    if (state.currentRoom == null) {
                        mutableUiState.update { current ->
                            current.copy(confirmation = null)
                        }
                        navigateAfterRoomLifecycleSuccess()
                    } else {
                        showFeedback(
                            message = "Unable to leave the room.",
                            isError = true
                        )
                    }
                }
                .onFailure { exception ->
                    showFailure(
                        exception,
                        RoomSessionOperation.LEAVE,
                        expectedRoomId
                    )
                }
        }
    }

    private fun archiveCurrentRoom() {
        if (roomLifecycleActionRole() != RoomMemberRole.OWNER) {
            showFeedback(OWNER_ACTION_REQUIRED_MESSAGE, isError = true)
            return
        }

        val expectedRoomId = currentRoomActionId()
        launchAction(RoomsUiOperation.ARCHIVE_ROOM) {
            coordinator.archiveCurrentRoom()
                .onSuccess { state ->
                    if (state.currentRoom == null) {
                        mutableUiState.update { current ->
                            current.copy(confirmation = null)
                        }
                        navigateAfterRoomLifecycleSuccess()
                    } else {
                        showFeedback(
                            message = "Unable to archive the room.",
                            isError = true
                        )
                    }
                }
                .onFailure { exception ->
                    showFailure(
                        exception,
                        RoomSessionOperation.ARCHIVE,
                        expectedRoomId
                    )
                }
        }
    }

    private fun currentRoomActionId(): String? =
        mutableUiState.value.roomDetailsRoomId
            ?: mutableUiState.value.currentRoom?.id

    private fun roomLifecycleActionRole(): RoomMemberRole? {
        val state = mutableUiState.value
        return if (state.roomDetailsRoomId == null) {
            state.currentRoom?.currentUserRole
        } else {
            state.roomDetails?.currentUserRole
        }
    }

    private fun navigateAfterRoomLifecycleSuccess() {
        if (mutableUiState.value.roomDetailsRoomId == null) {
            markRoomExitPending()
            emitEvent(RoomsUiEvent.NavigateToRooms)
        } else {
            navigateFromRoomDetailsOnce()
        }
    }

    private fun launchAction(
        operation: RoomsUiOperation,
        onAccepted: () -> Unit = {},
        action: suspend () -> Unit
    ) {
        if (isActionBlocked()) return

        onAccepted()
        mutableUiState.update { current ->
            current.copy(
                runningOperation = operation,
                feedback = null,
                sessionErrorMessage = null
            )
        }

        actionJob = viewModelScope.launch {
            try {
                action()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showFailure(exception)
            } finally {
                mutableUiState.update { current ->
                    if (current.runningOperation == operation) {
                        current.copy(runningOperation = null)
                    } else {
                        current
                    }
                }
                actionJob = null
            }
        }
    }

    private fun isActionBlocked(): Boolean =
        mutableUiState.value.roomExitPending ||
            actionJob?.isActive == true ||
            coordinator.state.value.isBusy

    private fun applySessionState(session: RoomSessionState) {
        val expectedRoomId = mutableUiState.value.roomDetailsRoomId
        val roomMatches = expectedRoomId != null &&
            session.currentRoom?.let { room ->
                room.id == expectedRoomId && room.status == RoomStatus.ACTIVE
            } == true
        if (roomMatches) {
            roomDetailsWasAvailable = true
        }
        val shouldExitRoomDetails = expectedRoomId != null &&
            roomDetailsWasAvailable &&
            !roomMatches

        mutableUiState.update { current ->
            current.copy(
                session = session,
                roomExitPending = current.roomExitPending ||
                    shouldExitRoomDetails,
                confirmation = if (shouldExitRoomDetails) null else current.confirmation,
                sessionErrorMessage = if (current.runningOperation == null) {
                    session.lastError.toDisplayMessageOrNull()
                } else {
                    current.sessionErrorMessage
                }
            )
        }

        if (shouldExitRoomDetails) {
            navigateFromRoomDetailsOnce()
        }
    }

    private fun navigateFromRoomDetailsOnce() {
        if (roomDetailsExitEventSent) return
        roomDetailsExitEventSent = true
        markRoomExitPending()
        emitEvent(RoomsUiEvent.NavigateToRooms)
    }

    private fun markRoomExitPending() {
        mutableUiState.update { current ->
            if (current.roomExitPending) {
                current
            } else {
                current.copy(
                    roomExitPending = true,
                    confirmation = null
                )
            }
        }
    }

    private fun clearRoomNameIfUnchanged(submittedValue: String) {
        if (mutableUiState.value.roomNameInput != submittedValue) return

        savedStateHandle[ROOM_NAME_STATE_KEY] = ""
        mutableUiState.update { current ->
            if (current.roomNameInput == submittedValue) {
                current.copy(roomNameInput = "")
            } else {
                current
            }
        }
    }

    private fun clearJoinRoomIdIfUnchanged(submittedValue: String) {
        if (mutableUiState.value.joinRoomIdInput != submittedValue) return

        savedStateHandle[JOIN_ROOM_ID_STATE_KEY] = ""
        mutableUiState.update { current ->
            if (current.joinRoomIdInput == submittedValue) {
                current.copy(joinRoomIdInput = "")
            } else {
                current
            }
        }
    }

    private fun showFailure(
        exception: Throwable,
        expectedOperation: RoomSessionOperation? = null,
        expectedRoomId: String? = null
    ) {
        val sessionError = coordinator.state.value.lastError
            .forOperation(expectedOperation, expectedRoomId)
        if (sessionError != null) {
            showSessionFailureOrFallback(sessionError, ROOM_OPERATION_FAILED_MESSAGE)
            return
        }
        showFeedback(exception.toSafeDisplayMessage(), isError = true)
    }

    private fun showSessionFailureOrFallback(
        error: RoomSessionError?,
        fallback: String
    ) {
        val message = error.toDisplayMessageOrNull()
        if (message == null) {
            showFeedback(fallback, isError = true)
            return
        }
        mutableUiState.update { current ->
            current.copy(
                feedback = null,
                sessionErrorMessage = message
            )
        }
    }

    private fun showFeedback(
        message: String,
        isError: Boolean = false
    ) {
        mutableUiState.update { current ->
            current.copy(
                feedback = RoomsUiFeedback(
                    message = message,
                    isError = isError
                )
            )
        }
    }

    private fun clearFeedback() {
        mutableUiState.update { current -> current.copy(feedback = null) }
    }

    private fun emitEvent(event: RoomsUiEvent) {
        eventsChannel.trySend(event)
    }

}

private fun String?.normalizedRoomName(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)

private fun String.normalizedRoomId(): String? =
    trim().takeIf(String::isNotEmpty)

private fun RoomSessionError?.forOperation(
    expectedOperation: RoomSessionOperation?,
    expectedRoomId: String?
): RoomSessionError? = this?.takeIf { error ->
    expectedOperation != null &&
        error.operation == expectedOperation &&
        (expectedRoomId == null ||
            error.roomId == null ||
            error.roomId == expectedRoomId)
}

private fun RoomSessionError.matches(
    expectedOperation: RoomSessionOperation,
    expectedRoomId: String?
): Boolean = forOperation(expectedOperation, expectedRoomId) != null

private fun RoomSessionError?.indicatesUnavailableRoom(): Boolean =
    this?.apiCode in setOf(
        "ROOM_ARCHIVED",
        "ROOM_NOT_FOUND",
        "ROOM_NOT_VISIBLE",
        "ROOM_MEMBER_REQUIRED",
        "ACCESS_DENIED"
    )

private fun RoomSessionError?.toDisplayMessageOrNull(): String? {
    this ?: return null
    if (type == OPERATION_IN_PROGRESS_ERROR_TYPE) return null

    return apiCode.toKnownRoomErrorMessage()
        ?: operation.toFallbackMessage(retryable)
}

private fun String?.toKnownRoomErrorMessage(): String? = when (this) {
    "ROOM_ARCHIVED" -> "This room has been archived."
    "ROOM_NOT_FOUND" -> "Room was not found."
    "ROOM_NOT_VISIBLE" -> "This room is not available to the current user."
    "ROOM_OWNER_CANNOT_LEAVE" ->
        "The room owner must archive the room instead of leaving it."
    "ROOM_OWNER_REQUIRED" -> "Only the room owner can perform this action."
    "ROOM_MEMBER_REQUIRED" ->
        "You are no longer an active member of this room."
    "PRIVATE_ROOM_REQUIRES_INVITE" ->
        "A private room can only be joined with an invite."
    "ACCESS_DENIED" -> "You do not have access to this room."
    "AUTHENTICATION_REQUIRED" -> "Your session has expired. Sign in again."
    "DEVICE_NOT_OWNED" ->
        "This device is not authorized for the current session."
    "VALIDATION_ERROR" -> "Check the room details and try again."
    else -> null
}

private fun Throwable.toSafeDisplayMessage(): String = when (this) {
    is ApiException -> apiError.code.toKnownRoomErrorMessage()
        ?: "ServeRelay could not complete the room operation."
    is IOException ->
        "Unable to reach ServeRelay. Check your connection and try again."
    is DeviceBootstrapRequiredException ->
        "Device setup is incomplete. Run bootstrap again."
    else -> ROOM_OPERATION_FAILED_MESSAGE
}

private fun String?.toRoomVisibility(): RoomVisibility =
    runCatching { RoomVisibility.valueOf(this.orEmpty()) }
        .getOrDefault(RoomVisibility.PRIVATE)

private fun RoomSessionOperation.toFallbackMessage(retryable: Boolean): String =
    if (retryable) {
        "ServeRelay is temporarily unavailable. Try again."
    } else {
        when (this) {
            RoomSessionOperation.RESTORE -> "Unable to restore the room session."
            RoomSessionOperation.LOAD_ROOMS -> "Unable to load rooms."
            RoomSessionOperation.OPEN_ROOM,
            RoomSessionOperation.ACTIVATE -> "Unable to open the room."
            RoomSessionOperation.DEACTIVATE -> ROOM_CLOSE_FAILED_MESSAGE
            RoomSessionOperation.REFRESH_ROOM,
            RoomSessionOperation.REFRESH_MEMBERS -> "Unable to refresh the room."
            RoomSessionOperation.CREATE -> "Unable to create the room."
            RoomSessionOperation.JOIN -> "Unable to join the room."
            RoomSessionOperation.LEAVE -> "Unable to leave the room."
            RoomSessionOperation.ARCHIVE -> "Unable to archive the room."
            RoomSessionOperation.RECONNECT -> "Unable to reconnect the room session."
        }
    }
