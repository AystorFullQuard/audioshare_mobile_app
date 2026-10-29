package mme.corp.audioshare.ui.screens.login

import androidx.compose.foundation.layout.*
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

@Composable
fun LoginScreen(onBack: () -> Unit, onLoginSuccess: () -> Unit) {
    val application = LocalContext.current.applicationContext as AudioShareApplication
    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.Factory(application.container.authRepository)
    )
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(ui.isLoggedIn) { if (ui.isLoggedIn) onLoginSuccess() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sign in to SvRe", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = ui.login,
                onValueChange = viewModel::updateLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username"),
                singleLine = true,
                label = { Text("Username or email") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(12.dp))
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
            ui.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::login,
                enabled = !ui.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_submit")
            ) {
                if (ui.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Sign in")
            }
            TextButton(onClick = onBack) { Text("Back to home") }
        }
    }
}
