package mme.corp.audioshare.ui.screens.login

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import mme.corp.audioshare.AudioShareApplication

private const val TAG = "LoginScreen"

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {

    Log.d(TAG, "==========================================")
    Log.d(TAG, "LoginScreen composed")

    val application =
        LocalContext.current.applicationContext
                as AudioShareApplication

    Log.d(TAG, "Application acquired")

    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.Factory(
            application.container.authRepository
        )
    )

    Log.d(TAG, "ViewModel acquired")

    val uiState by viewModel.uiState.collectAsState()

    Log.d(
        TAG,
        "UI State -> loading=${uiState.isLoading}, loggedIn=${uiState.isLoggedIn}, error=${uiState.error}"
    )

    LaunchedEffect(uiState.isLoggedIn) {

        Log.d(
            TAG,
            "LaunchedEffect fired. isLoggedIn=${uiState.isLoggedIn}"
        )

        if (uiState.isLoggedIn) {

            Log.i(TAG, "Calling onLoginSuccess()")

            try {

                onLoginSuccess()

                Log.i(TAG, "onLoginSuccess() finished")

            } catch (e: Exception) {

                Log.e(TAG, "onLoginSuccess() CRASHED", e)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "AudioShare",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = uiState.login,
                onValueChange = {

                    Log.d(TAG, "Login changed -> $it")

                    viewModel.updateLogin(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text("Login")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = {

                    Log.d(
                        TAG,
                        "Password changed (length=${it.length})"
                    )

                    viewModel.updatePassword(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text("Password")
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )

            uiState.error?.let {

                Log.w(TAG, "Displaying error: $it")

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                onClick = {

                    Log.i(TAG, "Login button clicked")

                    viewModel.login()
                }
            ) {

                if (uiState.isLoading) {

                    Log.d(TAG, "Showing loading indicator")

                    CircularProgressIndicator()

                } else {

                    Text("Login")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {

                    Log.d(TAG, "Create account clicked")
                }
            ) {

                Text("Create account")
            }
        }
    }

    Log.d(TAG, "LoginScreen composition finished")
}