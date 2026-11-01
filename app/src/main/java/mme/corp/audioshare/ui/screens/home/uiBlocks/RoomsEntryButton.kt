package mme.corp.audioshare.ui.screens.home.uiBlocks

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Navigation identity remains stable for instrumentation tests. */
@Composable
fun RoomsEntryButton(onNavigateRooms: () -> Unit, compact: Boolean = false) {
    if (compact) {
        TextButton(onClick = onNavigateRooms, modifier = Modifier.testTag(HOME_ROOMS_BUTTON_TAG)) {
            Text("View all")
        }
    } else {
        Button(onClick = onNavigateRooms, modifier = Modifier.testTag(HOME_ROOMS_BUTTON_TAG)) {
            Text("My Rooms")
        }
    }
}
