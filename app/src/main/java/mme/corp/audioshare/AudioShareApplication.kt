package mme.corp.audioshare

import android.app.Application
import mme.corp.audioshare.network.ApiClient
import mme.corp.audioshare.storage.SessionManager

class AudioShareApplication : Application() {

    lateinit var sessionManager: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()

        sessionManager = SessionManager(this)

        ApiClient.initialize(sessionManager)
    }
}