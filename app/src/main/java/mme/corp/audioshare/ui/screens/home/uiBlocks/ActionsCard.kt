package mme.corp.audioshare.ui.screens.home.uiBlocks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mme.corp.audioshare.ui.screens.home.HomeViewModel

const val HOME_ROOMS_BUTTON_TAG = "home_rooms_button"

@Composable
fun ActionsCard(
    viewModel: HomeViewModel,
    onNavigateRooms: () -> Unit = {}
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            RoomsEntryButton(onNavigateRooms)

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::refreshToken
            ) {
                Text("Refresh Token")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::clearSession
            ) {
                Text("Clear Session")
            }
        }
    }
}

@Composable
fun RoomsEntryButton(
    onNavigateRooms: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HOME_ROOMS_BUTTON_TAG),
        onClick = onNavigateRooms
    ) {
        Text("Rooms")
    }
}
