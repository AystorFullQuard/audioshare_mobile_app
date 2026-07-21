package mme.corp.audioshare.di

import mme.corp.audioshare.data.api.AuthApi
import mme.corp.audioshare.data.repository.AuthRepository
import mme.corp.audioshare.data.storage.SessionManager
import mme.corp.audioshare.network.ApiClient


class AppContainer(
    sessionManager: SessionManager
) {

    /*
     * APIs
     */

    private val authApi: AuthApi =
        ApiClient.create(AuthApi::class.java)

    /*
     * Repositories
     */

    val authRepository = AuthRepository(
        authApi = authApi,
        sessionManager = sessionManager
    )
}