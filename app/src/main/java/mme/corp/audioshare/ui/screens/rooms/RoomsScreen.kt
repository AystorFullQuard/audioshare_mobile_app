package mme.corp.audioshare.ui.screens.rooms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.room.RoomSessionCoordinator
import mme.corp.audioshare.room.RoomSessionOperation

object RoomsUiTestTags {
    const val LIST = "rooms_list"
    const val BACK = "rooms_back"
    const val LOADING = "rooms_loading"
    const val REFRESH = "rooms_refresh"
    const val CREATE_NAME = "rooms_create_name"
    const val CREATE_PRIVATE = "rooms_create_private"
    const val CREATE_LOCAL = "rooms_create_local"
    const val CREATE_SUBMIT = "rooms_create_submit"
    const val JOIN_ID = "rooms_join_id"
    const val JOIN_SUBMIT = "rooms_join_submit"
}

@Composable
fun RoomsScreen(
    coordinator: RoomSessionCoordinator,
    onBack: () -> Unit,
    onNavigateRoom: (String) -> Unit,
    onNavigateRooms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val factory = remember(coordinator) {
        RoomsViewModelFactory(coordinator)
    }
    val roomsViewModel: RoomsViewModel = viewModel(factory = factory)

    RoomsScreen(
        viewModel = roomsViewModel,
        onBack = onBack,
        onNavigateRoom = onNavigateRoom,
        onNavigateRooms = onNavigateRooms,
        modifier = modifier
    )
}

@Composable
internal fun RoomsScreen(
    viewModel: RoomsViewModel,
    onBack: () -> Unit,
    onNavigateRoom: (String) -> Unit,
    onNavigateRooms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.isRoomTransitionRunning) {
        // Keep lifecycle mutations attached to this destination until completion.
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is RoomsUiEvent.NavigateToRoom ->
                        onNavigateRoom(event.roomId)
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
        RoomsScreenContent(
            state = state,
            onAction = viewModel::onAction,
            onBack = onBack,
            modifier = Modifier.padding(contentPadding)
        )
    }
}

@Composable
fun RoomsScreenContent(
    state: RoomsUiState,
    onAction: (RoomsUiAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRoom = state.currentRoom?.takeIf { room ->
        room.status == RoomStatus.ACTIVE
    }
    val activeRooms = state.rooms.filter { room ->
        room.status == RoomStatus.ACTIVE && room.id != currentRoom?.id
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(RoomsUiTestTags.LIST)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RoomsHeader(
                backEnabled = !state.isRoomTransitionRunning,
                isBusy = state.isBusy,
                onBack = onBack,
                onRefresh = { onAction(RoomsUiAction.LoadRooms) }
            )
        }

        if (!state.session.isConnected || state.session.isStale) {
            item {
                ConnectionStatusCard(
                    isConnected = state.session.isConnected,
                    isStale = state.session.isStale
                )
            }
        }

        if (state.isBusy && activeRooms.isEmpty() && currentRoom == null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag(RoomsUiTestTags.LOADING)
                    )
                }
            }
        }

        val listError = state.session.lastError?.takeIf { error ->
            error.operation in ROOMS_LIST_ERROR_OPERATIONS
        }
        val listErrorMessage = listError?.let { state.sessionErrorMessage }
        listErrorMessage?.let { message ->
            val canRetryLoadRooms =
                listError.operation == RoomSessionOperation.LOAD_ROOMS &&
                    listError.retryable
            item {
                ErrorCard(
                    message = message,
                    retryable = canRetryLoadRooms,
                    enabled = !state.isBusy,
                    onRetry = { onAction(RoomsUiAction.LoadRooms) }
                )
            }
        }

        currentRoom?.let { room ->
            item {
                SectionTitle("Current room")
            }
            item {
                RoomCard(
                    room = room,
                    enabled = !state.isBusy,
                    onOpen = { onAction(RoomsUiAction.OpenRoom(room.id)) }
                )
            }
        }

        item {
            CreateRoomCard(
                state = state,
                onAction = onAction
            )
        }

        item {
            DebugJoinCard(
                state = state,
                onAction = onAction
            )
        }

        item {
            SectionTitle("My active rooms")
        }

        if (!state.isBusy && activeRooms.isEmpty()) {
            item {
                if (currentRoom == null) {
                    EmptyRoomsCard()
                } else {
                    NoOtherRoomsCard()
                }
            }
        }

        items(
            items = activeRooms,
            key = Room::id
        ) { room ->
            RoomCard(
                room = room,
                enabled = !state.isBusy,
                onOpen = { onAction(RoomsUiAction.OpenRoom(room.id)) }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun RoomsHeader(
    backEnabled: Boolean,
    isBusy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                modifier = Modifier.testTag(RoomsUiTestTags.BACK),
                enabled = backEnabled,
                onClick = onBack
            ) {
                Text("Back")
            }
            Text(
                text = "Rooms",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(
                modifier = Modifier.testTag(RoomsUiTestTags.REFRESH),
                enabled = !isBusy,
                onClick = onRefresh
            ) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
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
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ErrorCard(
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
private fun CreateRoomCard(
    state: RoomsUiState,
    onAction: (RoomsUiAction) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Create room",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = state.roomNameInput,
                onValueChange = {
                    onAction(RoomsUiAction.RoomNameChanged(it))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RoomsUiTestTags.CREATE_NAME),
                enabled = !state.isBusy,
                singleLine = true,
                label = { Text("Name (optional)") }
            )
            VisibilityOption(
                label = "Private",
                selected = state.createVisibility == RoomVisibility.PRIVATE,
                enabled = !state.isBusy,
                testTag = RoomsUiTestTags.CREATE_PRIVATE,
                onClick = {
                    onAction(
                        RoomsUiAction.CreateVisibilityChanged(
                            RoomVisibility.PRIVATE
                        )
                    )
                }
            )
            VisibilityOption(
                label = "Local discovery",
                selected = state.createVisibility ==
                    RoomVisibility.LOCAL_DISCOVERY,
                enabled = !state.isBusy,
                testTag = RoomsUiTestTags.CREATE_LOCAL,
                onClick = {
                    onAction(
                        RoomsUiAction.CreateVisibilityChanged(
                            RoomVisibility.LOCAL_DISCOVERY
                        )
                    )
                }
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RoomsUiTestTags.CREATE_SUBMIT),
                enabled = !state.isBusy,
                onClick = { onAction(RoomsUiAction.CreateRoom) }
            ) {
                Text("Create")
            }
        }
    }
}

@Composable
private fun VisibilityOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            modifier = Modifier.testTag(testTag),
            selected = selected,
            enabled = enabled,
            onClick = onClick
        )
        Text(label)
    }
}

@Composable
private fun DebugJoinCard(
    state: RoomsUiState,
    onAction: (RoomsUiAction) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Debug join",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Join a LOCAL_DISCOVERY room by room ID.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = state.joinRoomIdInput,
                onValueChange = {
                    onAction(RoomsUiAction.JoinRoomIdChanged(it))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RoomsUiTestTags.JOIN_ID),
                enabled = !state.isBusy,
                singleLine = true,
                label = { Text("Room ID") }
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RoomsUiTestTags.JOIN_SUBMIT),
                enabled = !state.isBusy,
                onClick = {
                    onAction(RoomsUiAction.JoinLocalDiscoveryRoom)
                }
            ) {
                Text("Join")
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun EmptyRoomsCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "No active rooms",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Create a room or join a LOCAL_DISCOVERY room by ID.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@Composable
private fun NoOtherRoomsCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "No other active rooms",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RoomCard(
    room: Room,
    enabled: Boolean,
    onOpen: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = room.displayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("Visibility: ${room.visibility.displayName()}")
            Text("Status: ${room.status.name}")
            Text("Role: ${room.currentUserRole?.name ?: "Unknown"}")
            Text(
                "Active members: ${room.activeMemberCount?.toString() ?: "Unknown"}"
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                onClick = onOpen
            ) {
                Text("Open")
            }
        }
    }
}

private val ROOMS_LIST_ERROR_OPERATIONS = setOf(
    RoomSessionOperation.RESTORE,
    RoomSessionOperation.LOAD_ROOMS,
    RoomSessionOperation.OPEN_ROOM,
    RoomSessionOperation.CREATE,
    RoomSessionOperation.JOIN,
    RoomSessionOperation.RECONNECT
)

private fun Room.displayName(): String =
    name?.trim()?.takeIf(String::isNotEmpty) ?: "Unnamed room"

private fun RoomVisibility.displayName(): String = when (this) {
    RoomVisibility.PRIVATE -> "Private"
    RoomVisibility.LOCAL_DISCOVERY -> "Local discovery"
}
