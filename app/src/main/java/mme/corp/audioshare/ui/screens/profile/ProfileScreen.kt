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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mme.corp.audioshare.AudioShareApplication
import mme.corp.audioshare.ui.components.SvReBackAction
import mme.corp.audioshare.ui.components.SvReInitialAvatar
import mme.corp.audioshare.ui.components.SvRePageHeading
import mme.corp.audioshare.ui.components.SvReSection

/** Never displays a fake account or a raw session credential. */
@Composable
fun ProfileScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val container = (LocalContext.current.applicationContext as AudioShareApplication).container
    val token by container.accessTokenState.collectAsStateWithLifecycle()
    val user by container.currentUser.collectAsStateWithLifecycle()
    val userId by container.sessionManager.userId.collectAsStateWithLifecycle(initialValue = null)
    var pending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token) { if (token.isNullOrBlank()) onLoggedOut() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SvReBackAction(onBack, Modifier.testTag("profile_back"))
            Spacer(Modifier.weight(1f))
            Text(
                "SvRe", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SvReInitialAvatar(user?.displayName?.firstOrNull()?.uppercaseChar()?.toString())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SvRePageHeading("My profile", "ServerRelay account")
                }
            }
            SvReSection {
                Text(user?.displayName?.takeIf { it.isNotBlank() } ?: "Signed-in account",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("profile_display_name"))
                ProfileField("Username", user?.username)
                ProfileField("Email", user?.email)
                ProfileField("Account ID", user?.id ?: userId)
            }
            SvReSection {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Text(
                    user?.settings?.theme ?: "Default",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Your preferences are provided by ServerRelay. Editing is not available yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(
                enabled = !pending,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
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
                            error = "Unable to sign out. Try again."
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
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
