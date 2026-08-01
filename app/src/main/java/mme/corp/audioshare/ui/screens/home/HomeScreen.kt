package mme.corp.audioshare.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import mme.corp.audioshare.AudioShareApplication
import mme.corp.audioshare.ui.screens.home.uiBlocks.ActionsCard
import mme.corp.audioshare.ui.screens.home.uiBlocks.AuthenticationCard
import mme.corp.audioshare.ui.screens.home.uiBlocks.StatusCard

@Composable
fun HomeScreen(
    onNavigateRooms: () -> Unit = {}
) {

    val application =
        LocalContext.current.applicationContext as AudioShareApplication

    val viewModel = remember {

        HomeViewModel(
            authRepository = application.container.authRepository,
            sessionManager = application.sessionManager,
            presenceRuntimeController = application.container.presenceLifecycleManager
        )

    }

    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSession()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),

        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "AudioShare",
            style = MaterialTheme.typography.headlineMedium
        )

        AuthenticationCard(state)

        ActionsCard(
            viewModel = viewModel,
            onNavigateRooms = onNavigateRooms
        )

        StatusCard(state)

    }

}