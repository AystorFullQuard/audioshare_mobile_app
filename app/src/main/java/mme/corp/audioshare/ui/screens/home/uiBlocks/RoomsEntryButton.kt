package mme.corp.audioshare.ui.screens.home.uiBlocks

import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Stable navigation action for existing Android UI tests. */
@Composable
fun RoomsEntryButton(onNavigateRooms: () -> Unit, compact: Boolean = false) {
    if (compact) {
        TextButton(onClick = onNavigateRooms, modifier = Modifier.testTag(HOME_ROOMS_BUTTON_TAG)) {
            Text("See All", color = Color(0xFFA49AFF))
            Text("›", color = Color(0xFF7566F1), fontSize = 22.sp)
        }
    } else {
        Button(onClick = onNavigateRooms, modifier = Modifier.testTag(HOME_ROOMS_BUTTON_TAG)) {
            Text("My Rooms")
        }
    }
}
