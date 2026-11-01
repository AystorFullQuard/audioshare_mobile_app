package mme.corp.audioshare.ui.screens.home

import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mme.corp.audioshare.AudioShareApplication
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import mme.corp.audioshare.ui.screens.navigation.AudioShareNavHost
import mme.corp.audioshare.ui.theme.AudioShareTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val app = applicationContext as AudioShareApplication
            val user by app.container.currentUser.collectAsStateWithLifecycle()
            val preferred = user?.settings?.theme?.uppercase()
            val dark = when (preferred) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            AudioShareTheme(darkTheme = dark) {
                AudioShareNavHost()
            }
        }
    }
}