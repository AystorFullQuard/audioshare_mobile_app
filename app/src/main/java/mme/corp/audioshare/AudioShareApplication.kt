package mme.corp.audioshare

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import mme.corp.audioshare.data.storage.SessionManager
import mme.corp.audioshare.di.AppContainer
import mme.corp.audioshare.network.retrofit.ApiClient

class AudioShareApplication : Application() {

    lateinit var sessionManager: SessionManager
        private set

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        sessionManager = SessionManager(this)
        ApiClient.initialize(sessionManager)
        container = AppContainer(sessionManager)

        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(container.presenceLifecycleObserver)
    }
}
