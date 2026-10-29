package mme.corp.audioshare.data.repository

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.api.AuthApi
import mme.corp.audioshare.data.dto.LoginRequest
import mme.corp.audioshare.data.dto.LoginResponse
import mme.corp.audioshare.data.dto.RefreshRequest
import mme.corp.audioshare.data.storage.SessionManager
import mme.corp.audioshare.network.retrofit.executeApiCall
import mme.corp.audioshare.presence.PresenceRuntimeController
import mme.corp.audioshare.room.RoomSessionRuntimeController
import mme.corp.audioshare.session.JwtSessionDecoder

/** Network CSRF/JWT/refresh handling belongs to ApiClient, not each repository. */
class AuthRepository(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val presenceRuntimeController: PresenceRuntimeController =
        PresenceRuntimeController.NO_OP,
    private val roomSessionRuntimeController: RoomSessionRuntimeController =
        RoomSessionRuntimeController.NO_OP,
    private val disconnectSignaling: () -> Unit = {}
) {
    suspend fun login(login: String, password: String): Result<LoginResponse> =
        persistResponse {
            authApi.login(LoginRequest(login = login, password = password))
        }

    suspend fun refresh(): Result<LoginResponse> {
        val refreshToken = sessionManager.getRefreshToken()
            ?.takeIf(String::isNotBlank)
            ?: return Result.failure(IllegalStateException("Refresh token is unavailable"))
        return persistResponse {
            authApi.refresh(RefreshRequest(refreshToken = refreshToken))
        }
    }

    private suspend fun persistResponse(
        call: suspend () -> retrofit2.Response<LoginResponse>
    ): Result<LoginResponse> {
        val result = executeApiCall("Authentication response is empty", call)
        if (result.isFailure) return result
        val body = result.getOrThrow()
        return try {
            require(
                body.session.accessToken.isNotBlank() &&
                        body.session.refreshToken.isNotBlank()
            ) {
                "Authentication response contains empty tokens"
            }
            sessionManager.saveSession(
                accessToken = body.session.accessToken,
                refreshToken = body.session.refreshToken,
                userId = body.user.id,
                sessionId = JwtSessionDecoder.sessionId(body.session.accessToken)
            )
            Result.success(body)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /** Local logout must succeed even if the server is unreachable. */
    suspend fun logout(): Result<Unit> {
        presenceRuntimeController.stop()
        roomSessionRuntimeController.clearForLogout()
        disconnectSignaling()
        try {
            // Logout is best-effort over the network; never retain the local token for retry.
            authApi.logout()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Network failure does not prevent device-local logout.
        }
        return try {
            sessionManager.clearSession()
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
