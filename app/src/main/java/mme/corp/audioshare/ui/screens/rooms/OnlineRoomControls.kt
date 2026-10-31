package mme.corp.audioshare.ui.screens.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import mme.corp.audioshare.room.OnlineCallCoordinator
import mme.corp.audioshare.data.repository.InviteRepository

/** Online-only backend call/invite control. Does not start microphone or UDP media. */
@Composable
fun OnlineRoomControls(roomId: String, calls: OnlineCallCoordinator, invites: InviteRepository) {
    val state by calls.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var inviteCode by remember(roomId) { mutableStateOf<String?>(null) }
    var isInviteBusy by remember(roomId) { mutableStateOf(false) }
    LaunchedEffect(roomId) { calls.attach(roomId) }
    DisposableEffect(roomId, calls) {
        onDispose { inviteCode = null; calls.detach() }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Online call controls", style = MaterialTheme.typography.titleMedium)
            Text(
                "Server control only — encrypted local audio is not yet enabled.",
                style = MaterialTheme.typography.bodySmall
            )
            state.call?.let { call ->
                Text("Call status: ${call.status}")
                Text("Participants: ${call.participants.size}")
            }
            if (state.roomId == roomId) state.error?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { coroutineScope.launch { calls.load(roomId) } },
                    enabled = !state.busy
                ) {
                    Text("Refresh")
                }
                Button(
                    onClick = { coroutineScope.launch { calls.start(roomId) } },
                    enabled = !state.busy &&
                            (state.call == null || state.call?.status == "ENDED")
                ) {
                    Text("Start call")
                }
            }
            state.call?.takeIf { it.status != "ENDED" }?.let { call ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { coroutineScope.launch { calls.join(roomId, call.id) } },
                        enabled = !state.busy
                    ) { Text("Join") }
                    OutlinedButton(
                        onClick = { coroutineScope.launch { calls.leave(roomId, call.id) } },
                        enabled = !state.busy
                    ) { Text("Leave") }
                    OutlinedButton(
                        onClick = { coroutineScope.launch { calls.end(roomId, call.id) } },
                        enabled = !state.busy
                    ) { Text("End") }
                }
            }
            OutlinedButton(enabled = !isInviteBusy, onClick = {
                coroutineScope.launch {
                    isInviteBusy = true
                    try {
                        inviteCode = invites.create(roomId, expiresInMinutes = 10, maxUses = 1)
                            .getOrNull()?.inviteCode
                    } finally {
                        isInviteBusy = false
                    }
                }
            }) { Text("Create one-use invite") }
            inviteCode?.let { code ->
                Text("Invite code: $code")
                OutlinedButton(onClick = { inviteCode = null }) { Text("Hide code") }
            }
        }
    }
}
