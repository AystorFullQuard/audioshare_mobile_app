package mme.corp.audioshare.di

import mme.corp.audioshare.data.api.AuthApi
import mme.corp.audioshare.data.api.BootstrapApi
import mme.corp.audioshare.data.api.PresenceApi
import mme.corp.audioshare.data.repository.AuthRepository
import mme.corp.audioshare.data.repository.BootstrapRepository
import mme.corp.audioshare.data.repository.PresenceRepository
import mme.corp.audioshare.data.storage.SessionManager
import mme.corp.audioshare.network.retrofit.ApiClient


class AppContainer(
    val sessionManager: SessionManager
) {

    /*
     * APIs
     */

    private val authApi: AuthApi =
        ApiClient.create(AuthApi::class.java)

    private val bootstrapApi: BootstrapApi =
        ApiClient.create(BootstrapApi::class.java)

    private val presenceApi: PresenceApi =
        ApiClient.create(PresenceApi::class.java)

    /*
     * Repositories
     */

    val authRepository = AuthRepository(
        authApi = authApi,
        sessionManager = sessionManager
    )

    val bootstrapRepository = BootstrapRepository(
        bootstrapApi = bootstrapApi,
        deviceIdStore = sessionManager
    )

    val presenceRepository = PresenceRepository(
        presenceApi = presenceApi,
        deviceIdStore = sessionManager
    )
}