package mme.corp.audioshare.session

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import mme.corp.audioshare.backend.ApiResult
import mme.corp.audioshare.backend.auth.AuthApi
import mme.corp.audioshare.backend.auth.DevLoginRequest
import mme.corp.audioshare.backend.auth.LoginResponse
import mme.corp.audioshare.backend.auth.MeResponse
import mme.corp.audioshare.backend.bootstrap.BootstrapApi
import mme.corp.audioshare.backend.bootstrap.BootstrapRequest
import mme.corp.audioshare.backend.bootstrap.BootstrapResponse
import mme.corp.audioshare.backend.devices.DeviceResponse
import mme.corp.audioshare.backend.devices.DevicesApi
import mme.corp.audioshare.backend.executeApiCall
import mme.corp.audioshare.backend.presence.PresenceApi
import mme.corp.audioshare.backend.presence.PresenceHeartbeatRequest
import mme.corp.audioshare.backend.presence.PresenceHeartbeatResponse
import mme.corp.audioshare.backend.storage.BackendSession
import mme.corp.audioshare.backend.storage.SessionStore

class BackendSessionRepository(
    private val authApi: AuthApi,
    private val bootstrapApi: BootstrapApi,
    private val presenceApi: PresenceApi,
    private val devicesApi: DevicesApi,
    private val sessionStore: SessionStore,
    private val json: Json
) {
    val session: StateFlow<BackendSession> = sessionStore.session

    suspend fun loadStoredSession(): BackendSession = sessionStore.load()

    suspend fun devRegister(): ApiResult<LoginResponse> = executeApiCall(json) {
        authApi.devRegister()
    }.persistAuthenticationOnSuccess()

    suspend fun devLogin(userId: String): ApiResult<LoginResponse> = executeApiCall(json) {
        authApi.devLogin(DevLoginRequest(userId))
    }.persistAuthenticationOnSuccess()

    suspend fun getCurrentUser(): ApiResult<MeResponse> {
        sessionStore.load()
        return executeApiCall(json, authApi::me)
    }

    suspend fun bootstrap(request: BootstrapRequest): ApiResult<BootstrapResponse> {
        sessionStore.load()
        return executeApiCall(json) {
            bootstrapApi.bootstrap(request)
        }.also { result ->
            if (result is ApiResult.Success) {
                sessionStore.saveDeviceId(result.value.deviceId)
            }
        }
    }

    suspend fun heartbeat(
        request: PresenceHeartbeatRequest
    ): ApiResult<PresenceHeartbeatResponse> {
        sessionStore.load()
        return executeApiCall(json) {
            presenceApi.heartbeat(request)
        }
    }

    suspend fun getDevices(): ApiResult<List<DeviceResponse>> {
        sessionStore.load()
        return executeApiCall(json, devicesApi::getDevices)
    }

    suspend fun clearSession() = sessionStore.clear()

    private suspend fun ApiResult<LoginResponse>.persistAuthenticationOnSuccess(): ApiResult<LoginResponse> {
        if (this is ApiResult.Success) {
            sessionStore.saveAuthentication(
                userId = value.userId,
                sessionId = value.sessionId,
                accessToken = value.accessToken,
                refreshToken = value.refreshToken
            )
        }
        return this
    }
}
