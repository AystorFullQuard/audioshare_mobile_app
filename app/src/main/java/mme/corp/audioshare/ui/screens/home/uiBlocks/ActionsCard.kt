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
import androidx.compose.ui.unit.dp
import mme.corp.audioshare.ui.screens.home.HomeViewModel

@Composable
fun ActionsCard(
    viewModel: HomeViewModel
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {

                    viewModel.refreshToken()

                }
            ) {

                Text("Refresh Token")

            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {

                    viewModel.clearSession()

                }
            ) {

                Text("Clear Session")

            }

        }

    }

}