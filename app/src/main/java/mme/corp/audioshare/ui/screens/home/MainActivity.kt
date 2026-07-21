package mme.corp.audioshare.ui.screens.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import mme.corp.audioshare.ui.screens.navigation.AudioShareNavHost
import mme.corp.audioshare.ui.theme.AudioShareTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AudioShareTheme {
                AudioShareNavHost()
            }
        }
    }
}