package mme.corp.audioshare.ui.screens.rooms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomMemberState
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.model.room.RoomMember
import mme.corp.audioshare.room.RoomSessionCoordinator
import mme.corp.audioshare.room.RoomSessionOperation

object RoomUiTestTags {
    const val LIST = "room_details_list"
    const val BACK = "room_details_back"
    const val LOADING = "room_details_loading"
    const val REFRESH = "room_details_refresh"
    const val ARCHIVE = "room_details_archive"
    const val LEAVE = "room_details_leave"
    const val CONFIRM = "room_details_confirm"
    const val CANCEL = "room_details_cancel"

    fun member(membershipId: String): String = "room_member_$membershipId"
}

@Composable
fun RoomScreen(
    roomId: String,
    coordinator: RoomSessionCoordinator,
    onBack: () -> Unit,
    onNavigateRooms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val factory = remember(coordinator) {
        RoomsViewModelFactory(coordinator)
    }
    val roomsViewModel: RoomsViewModel = viewModel(factory = factory)

    RoomScreen(
        roomId = roomId,
        viewModel = roomsViewModel,
        onBack = onBack,
        onNavigateRooms = onNavigateRooms,
        modifier = modifier
    )
}

@Composable
internal fun RoomScreen(
    roomId: String,
    viewModel: RoomsViewModel,
    onBack: () -> Unit,
    onNavigateRooms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.isRoomTransitionRunning) {
        // Keep the destination alive until the server-authoritative exit completes.
    }

    LaunchedEffect(
        roomId,
        state.session.isBusy,
        state.runningOperation,
        viewModel
    ) {
        if (!state.isBusy) {
            viewModel.onAction(RoomsUiAction.OpenRoomDetails(roomId))
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is RoomsUiEvent.NavigateToRoom -> Unit
                    RoomsUiEvent.NavigateToRooms -> onNavigateRooms()
                }
            }
        }
    }

    LaunchedEffect(state.feedback) {
        val feedback = state.feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(feedback.message)
        viewModel.onAction(RoomsUiAction.FeedbackConsumed)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
        RoomScreenContent(
            roomId = roomId,
            state = state,
            onAction = viewModel::onAction,
            onBack = onBack,
            modifier = Modifier.padding(contentPadding)
        )
    }
}

@Composable
fun RoomScreenContent(
    roomId: String,
    state: RoomsUiState,
    onAction: (RoomsUiAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentation = state.toRoomScreenPresentation(roomId)

    RoomScreenList(
        state = state,
        presentation = presentation,
        onAction = onAction,
        onBack = onBack,
        modifier = modifier
    )

    state.confirmation?.let { confirmation ->
        RoomActionConfirmationDialog(
            confirmation = confirmation,
            enabled = !state.isBusy,
            onConfirm = { onAction(RoomsUiAction.ConfirmRoomAction) },
            onDismiss = { onAction(RoomsUiAction.DismissConfirmation) }
        )
    }
}

@Composable
private fun RoomScreenList(
    state: RoomsUiState,
    presentation: RoomScreenPresentation,
    onAction: (RoomsUiAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(RoomUiTestTags.LIST)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RoomHeader(
                backEnabled = !state.isRoomTransitionRunning,
                refreshEnabled = presentation.room != null && !state.isBusy,
                onBack = onBack,
                onRefresh = { onAction(RoomsUiAction.RefreshCurrentRoom) }
            )
        }

        if (!state.session.isConnected || state.session.isStale) {
            item {
                RoomConnectionStatusCard(
                    isConnected = state.session.isConnected,
                    isStale = state.session.isStale
                )
            }
        }

        if (presentation.showLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag(RoomUiTestTags.LOADING)
                    )
                }
            }
        }

        presentation.errorMessage?.let { message ->
            item {
                RoomErrorCard(
                    message = message,
                    retryable = presentation.canRetry,
                    enabled = !state.isBusy,
                    onRetry = { onAction(RoomsUiAction.RetryRoomDetails) }
                )
            }
        }

        if (presentation.showUnavailable) {
            item {
                RoomErrorCard(
                    message = "Room is not available.",
                    retryable = true,
                    enabled = true,
                    onRetry = { onAction(RoomsUiAction.RetryRoomDetails) }
                )
            }
        }

        presentation.room?.let { activeRoom ->
            item {
                RoomDetailsCard(activeRoom)
            }

            item {
                Text(
                    text = "Active members",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (presentation.showEmptyMembers) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No active members",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(
                items = presentation.activeMembers,
                key = RoomMember::membershipId
            ) { member ->
                RoomMemberRow(member)
            }

            item {
                RoomLifecycleActions(
                    role = activeRoom.currentUserRole,
                    enabled = !state.isBusy,
                    onAction = onAction
                )
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

private data class RoomScreenPresentation(
    val room: Room?,
    val activeMembers: List<RoomMember>,
    val errorMessage: String?,
    val canRetry: Boolean,
    val showLoading: Boolean,
    val showUnavailable: Boolean,
    val showEmptyMembers: Boolean
)

private fun RoomsUiState.toRoomScreenPresentation(
    roomId: String
): RoomScreenPresentation {
    val activeRoom = currentRoom?.takeIf { room ->
        room.id == roomId && room.status == RoomStatus.ACTIVE
    }
    val members = activeMembers.filter { member ->
        member.roomId == roomId &&
            member.membershipState == RoomMemberState.ACTIVE
    }
    val relevantError = session.lastError?.takeIf { error ->
        error.operation in ROOM_DETAILS_ERROR_OPERATIONS &&
            (error.roomId == null || error.roomId == roomId)
    }
    val message = relevantError?.let { sessionErrorMessage }
    val loading = isBusy || roomDetailsRoomId != roomId

    return RoomScreenPresentation(
        room = activeRoom,
        activeMembers = members,
        errorMessage = message,
        canRetry = relevantError?.retryable == true &&
            relevantError.operation in ROOM_DETAILS_RETRY_OPERATIONS,
        showLoading = loading,
        showUnavailable = activeRoom == null && !loading && message == null,
        showEmptyMembers = activeRoom != null && members.isEmpty() && !isBusy
    )
}

@Composable
private fun RoomHeader(
    backEnabled: Boolean,
    refreshEnabled: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(
            modifier = Modifier
                .testTag(RoomUiTestTags.BACK)
                .semantics { contentDescription = "Back to rooms" },
            enabled = backEnabled,
            onClick = onBack
        ) {
            Text("Back")
        }
        Text(
            text = "Room",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(
            modifier = Modifier
                .testTag(RoomUiTestTags.REFRESH)
                .semantics { contentDescription = "Refresh room details" },
            enabled = refreshEnabled,
            onClick = onRefresh
        ) {
            Text("Refresh")
        }
    }
}

@Composable
private fun RoomConnectionStatusCard(
    isConnected: Boolean,
    isStale: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when {
                !isConnected && isStale ->
                    "Offline. Showing the last confirmed room state."
                !isConnected -> "ServeRelay is currently unavailable."
                else -> "Room information may be out of date."
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun RoomErrorCard(
    message: String,
    retryable: Boolean,
    enabled: Boolean,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (retryable) {
                OutlinedButton(
                    enabled = enabled,
                    onClick = onRetry
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun RoomDetailsCard(room: Room) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = room.displayName(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("Room ID: ${room.id}")
            Text("Visibility: ${room.visibility.displayName()}")
            Text("Status: ${room.status.name}")
            Text("Your role: ${room.currentUserRole?.name ?: "Unknown"}")
            Text(
                "Active members: ${room.activeMemberCount?.toString() ?: "Unknown"}"
            )
        }
    }
}

@Composable
private fun RoomMemberRow(member: RoomMember) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RoomUiTestTags.member(member.membershipId))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatarPlaceholder(member.displayLabel())
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = member.displayLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Role: ${member.role.name}")
                Text("Membership: ${member.membershipState.name}")
                Text("Presence: ${member.presenceState.displayName()}")
                Text(
                    "Last seen: ${member.presenceLastSeenAt ?: "Hidden"}"
                )
            }
        }
    }
}

@Composable
private fun MemberAvatarPlaceholder(displayLabel: String) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .semantics {
                contentDescription = "Avatar for $displayLabel"
            },
        shape = CircleShape,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayLabel.firstOrNull()
                    ?.uppercaseChar()
                    ?.toString()
                    ?: "?",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun RoomLifecycleActions(
    role: RoomMemberRole?,
    enabled: Boolean,
    onAction: (RoomsUiAction) -> Unit
) {
    when (role) {
        RoomMemberRole.OWNER -> {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RoomUiTestTags.ARCHIVE),
                enabled = enabled,
                onClick = {
                    onAction(RoomsUiAction.RequestArchiveConfirmation)
                }
            ) {
                Text("Archive room")
            }
        }
        RoomMemberRole.MEMBER -> {
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RoomUiTestTags.LEAVE),
                enabled = enabled,
                onClick = {
                    onAction(RoomsUiAction.RequestLeaveConfirmation)
                }
            ) {
                Text("Leave room")
            }
        }
        null -> Unit
    }
}

@Composable
private fun RoomActionConfirmationDialog(
    confirmation: RoomActionConfirmation,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isArchive = confirmation == RoomActionConfirmation.ARCHIVE
    AlertDialog(
        onDismissRequest = {
            if (enabled) onDismiss()
        },
        title = {
            Text(if (isArchive) "Archive room?" else "Leave room?")
        },
        text = {
            Text(
                if (isArchive) {
                    "Archiving closes this room for every active member."
                } else {
                    "You will leave this room and return to the rooms list."
                }
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(RoomUiTestTags.CONFIRM),
                enabled = enabled,
                onClick = onConfirm
            ) {
                Text(if (isArchive) "Archive" else "Leave")
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(RoomUiTestTags.CANCEL),
                enabled = enabled,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

private val ROOM_DETAILS_ERROR_OPERATIONS = setOf(
    RoomSessionOperation.OPEN_ROOM,
    RoomSessionOperation.REFRESH_ROOM,
    RoomSessionOperation.REFRESH_MEMBERS,
    RoomSessionOperation.LEAVE,
    RoomSessionOperation.ARCHIVE
)

private val ROOM_DETAILS_RETRY_OPERATIONS = setOf(
    RoomSessionOperation.OPEN_ROOM,
    RoomSessionOperation.REFRESH_ROOM,
    RoomSessionOperation.REFRESH_MEMBERS
)

private fun Room.displayName(): String =
    name?.trim()?.takeIf(String::isNotEmpty) ?: "Unnamed room"

private fun RoomVisibility.displayName(): String = when (this) {
    RoomVisibility.PRIVATE -> "Private"
    RoomVisibility.LOCAL_DISCOVERY -> "Local discovery"
}

private fun RoomMember.displayLabel(): String =
    displayName?.trim()?.takeIf(String::isNotEmpty)
        ?: username?.trim()?.takeIf(String::isNotEmpty)
        ?: "Unknown member"

private fun PresenceState?.displayName(): String = this?.name ?: "Unknown"
