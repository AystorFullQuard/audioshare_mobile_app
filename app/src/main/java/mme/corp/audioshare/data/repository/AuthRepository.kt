package mme.corp.audioshare.data.repository

import mme.corp.audioshare.data.api.AuthApi
import mme.corp.audioshare.data.dto.LoginRequest
import mme.corp.audioshare.data.dto.LoginResponse
import mme.corp.audioshare.data.storage.SessionManager

class AuthRepository(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager
) {

    suspend fun login(
        login: String,
        password: String
    ): Result<LoginResponse> {

        val response = authApi.login(
            LoginRequest(
                login = login,
                password = password
            )
        )

        if (!response.isSuccessful) {
            return Result.failure(
                Exception("Login failed (${response.code()})")
            )
        }

        val body = response.body()
            ?: return Result.failure(
                Exception("Empty response")
            )

        sessionManager.saveSession(
            accessToken = body.accessToken,
            refreshToken = body.refreshToken,
            userId = body.userId,
            sessionId = body.sessionId
        )

        return Result.success(body)
    }
}