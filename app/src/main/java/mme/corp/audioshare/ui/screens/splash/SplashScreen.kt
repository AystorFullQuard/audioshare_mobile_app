package mme.corp.audioshare.ui.screens.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import mme.corp.audioshare.AudioShareApplication

@Composable
fun SplashScreen(
    onNavigateLogin: () -> Unit,
    onNavigateBootstrap: () -> Unit
) {

    val application =
        LocalContext.current.applicationContext
                as AudioShareApplication

    val viewModel: SplashViewModel = viewModel(
        factory = SplashViewModel.Factory(
            application.sessionManager
        )
    )

    val destination by viewModel.destination.collectAsState()

    LaunchedEffect(destination) {
        when (destination) {

            SplashDestination.Login ->
                onNavigateLogin()

            SplashDestination.Bootstrap ->
                onNavigateBootstrap()

            SplashDestination.Loading -> {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "AudioShare",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            CircularProgressIndicator()
        }
    }
}