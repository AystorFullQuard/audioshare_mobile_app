package mme.corp.audioshare

import android.content.Context
import android.content.pm.ApplicationInfo
import mme.corp.audioshare.backend.ServeRelayConfig
import mme.corp.audioshare.backend.ServeRelayHttpClient
import mme.corp.audioshare.backend.storage.DataStoreSessionStore
import mme.corp.audioshare.session.BackendSessionRepository

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val sessionStore = DataStoreSessionStore(applicationContext)
    private val httpClient = ServeRelayHttpClient.create(
        config = ServeRelayConfig(
            baseUrl = BuildConfig.SERVERELAY_BASE_URL,
            enableHttpLogging = (applicationContext.applicationInfo.flags and
                ApplicationInfo.FLAG_DEBUGGABLE) != 0
        ),
        sessionStore = sessionStore
    )

    val backendSessionRepository = BackendSessionRepository(
        authApi = httpClient.authApi,
        bootstrapApi = httpClient.bootstrapApi,
        presenceApi = httpClient.presenceApi,
        devicesApi = httpClient.devicesApi,
        sessionStore = sessionStore,
        json = httpClient.json
    )
}
