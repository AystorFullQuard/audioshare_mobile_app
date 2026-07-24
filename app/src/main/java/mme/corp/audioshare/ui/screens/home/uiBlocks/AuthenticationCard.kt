package mme.corp.audioshare.ui.screens.home.uiBlocks


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mme.corp.audioshare.ui.screens.home.HomeUiState

@Composable
fun AuthenticationCard(
    state: HomeUiState
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                "Authentication",
                style = MaterialTheme.typography.titleLarge
            )

            Text("")

            Text("User ID")

            Text(
                state.userId ?: "Not available"
            )

            Text("")

            Text("Session ID")

            Text(
                state.sessionId ?: "Not available"
            )

            Text("")

            Text(
                "Access Token: ${
                    if (state.accessTokenExists) "✓ Stored (${state.accessTokenLength})"
                    else "Not Stored"
                }"
            )

            Text(
                "Refresh Token: ${
                    if (state.refreshTokenExists) "✓ Stored (${state.refreshTokenLength})"
                    else "Not Stored"
                }"
            )

        }

    }

}