package mme.corp.audioshare.ui.screens.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mme.corp.audioshare.AudioShareApplication
import mme.corp.audioshare.BuildConfig
import mme.corp.audioshare.network.retrofit.NetworkConfig
import mme.corp.audioshare.ui.components.SvReBackAction
import mme.corp.audioshare.ui.components.SvRePageHeading
import mme.corp.audioshare.ui.components.SvReSection

/** Login stays optional at startup and never exposes sensitive CSRF/JWT details in UI. */
@Composable
fun LoginScreen(onBack: () -> Unit, onLoginSuccess: () -> Unit) {
    val app = LocalContext.current.applicationContext as AudioShareApplication
    val viewModel: LoginViewModel =
        viewModel(factory = LoginViewModel.Factory(app.container.authRepository))
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(ui.isLoggedIn) { if (ui.isLoggedIn) onLoginSuccess() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SvReBackAction(onBack)
                Spacer(Modifier.weight(1f))
                Text(
                    "SvRe", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
            SvRePageHeading("Sign in", "Connect to your ServerRelay account to manage rooms")
            SvReSection {
                OutlinedTextField(
                    value = ui.login, onValueChange = viewModel::updateLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_username"),
                    singleLine = true, label = { Text("Username or email") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = ui.password,
                    onValueChange = viewModel::updatePassword,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password"),
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
                ui.error?.let {
                    Text(
                        it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = viewModel::login, enabled = !ui.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("login_submit")
                ) {
                    if (ui.isLoading) CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    else Text("Sign in")
                }
            }
            if (BuildConfig.DEBUG) {
                Text(
                    "Development server · ${NetworkConfig.BASE_URL}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "Emulator: 10.0.2.2 · Physical device: configure your PC's private IPv4 in Gradle and debug network security",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
