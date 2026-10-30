package mme.corp.audioshare.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mme.corp.audioshare.AudioShareApplication

/** Authenticated account details are sourced from the login/bootstrap response, never a fake profile. */
@Composable
fun ProfileScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val container = (LocalContext.current.applicationContext as AudioShareApplication).container
    val token by container.accessTokenState.collectAsStateWithLifecycle()
    val user by container.currentUser.collectAsStateWithLifecycle()
    val userId by container.sessionManager.userId.collectAsStateWithLifecycle(initialValue = null)
    var pending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // A revoked/cleared session must not leave a private page open.
    LaunchedEffect(token) { if (token.isNullOrBlank()) onLoggedOut() }

    Scaffold(topBar = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("profile_back")
            ) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text(
                "My Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(user?.displayName?.takeIf { it.isNotBlank() } ?: "Signed-in account",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("profile_display_name"))
            ProfileField("Username", user?.username)
            ProfileField("Email", user?.email)
            ProfileField("Account ID", user?.id ?: userId)
            ProfileField("Appearance", user?.settings?.theme)
            Text(
                "Profile information is provided by ServerRelay. Account settings cannot be edited here yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(
                enabled = !pending,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_logout"),
                onClick = {
                    if (pending) return@OutlinedButton
                    scope.launch {
                        pending = true
                        error = null
                        try {
                            container.authRepository.logout().getOrThrow()
                            container.currentUser.value = null
                            onLoggedOut()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "Could not clear your local session. Try again."
                        } finally {
                            pending = false
                        }
                    }
                }
            ) { Text(if (pending) "Signing out…" else "Sign out") }
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
