package mme.corp.audioshare.repository

import mme.corp.audioshare.network.api.AuthApi
import mme.corp.audioshare.network.dto.LoginRequest
import mme.corp.audioshare.storage.SessionManager

class AuthRepository(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager
) {

    suspend fun login(
        username: String,
        password: String
    ): Result<Unit> {

        val response = authApi.login(
            LoginRequest(username, password)
        )

        if (response.isSuccessful) {

            val body = response.body()
                ?: return Result.failure(Exception("Empty response"))

            sessionManager.saveAccessToken(body.accessToken)
            sessionManager.saveRefreshToken(body.refreshToken)

            return Result.success(Unit)
        }

        return Result.failure(
            Exception("Login failed")
        )
    }
}