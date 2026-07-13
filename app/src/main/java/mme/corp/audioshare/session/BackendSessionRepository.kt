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

data class BackendSessionStartupRequest(
    val displayName: String?,
    val deviceName: String?,
    val appVersion: String?
)

data class BackendSessionSnapshot(
    val userId: String,
    val deviceId: String,
    val deviceName: String?,
    val displayName: String?,
    val lastHeartbeatAt: String,
    val deviceCount: Int
)

enum class BackendSessionStep {
    AUTHENTICATION,
    CURRENT_USER,
    DEVICE_BOOTSTRAP,
    PRESENCE_HEARTBEAT,
    DEVICES
}

sealed interface BackendSessionStartupResult {
    data class Success(
        val snapshot: BackendSessionSnapshot
    ) : BackendSessionStartupResult

    data class Failure(
        val step: BackendSessionStep,
        val apiFailure: ApiResult.Failure
    ) : BackendSessionStartupResult
}

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

    suspend fun initializeSession(
        request: BackendSessionStartupRequest
    ): BackendSessionStartupResult {
        val storedSession = sessionStore.load()
        val authenticationFailure = ensureAuthentication(storedSession)
        if (authenticationFailure != null) {
            return BackendSessionStartupResult.Failure(
                step = BackendSessionStep.AUTHENTICATION,
                apiFailure = authenticationFailure
            )
        }

        val me = getCurrentUserWithDevRecovery()
        if (me is ApiResult.Failure) {
            return BackendSessionStartupResult.Failure(
                step = BackendSessionStep.CURRENT_USER,
                apiFailure = me
            )
        }

        val currentSession = sessionStore.load()
        val bootstrap = bootstrap(
            BootstrapRequest(
                deviceId = currentSession.deviceId,
                displayName = request.displayName,
                deviceName = request.deviceName,
                appVersion = request.appVersion
            )
        )
        if (bootstrap is ApiResult.Failure) {
            return BackendSessionStartupResult.Failure(
                step = BackendSessionStep.DEVICE_BOOTSTRAP,
                apiFailure = bootstrap
            )
        }

        val bootstrapResponse = (bootstrap as ApiResult.Success).value
        val heartbeat = heartbeat(
            PresenceHeartbeatRequest(deviceId = bootstrapResponse.deviceId)
        )
        if (heartbeat is ApiResult.Failure) {
            return BackendSessionStartupResult.Failure(
                step = BackendSessionStep.PRESENCE_HEARTBEAT,
                apiFailure = heartbeat
            )
        }

        val devices = getDevices()
        if (devices is ApiResult.Failure) {
            return BackendSessionStartupResult.Failure(
                step = BackendSessionStep.DEVICES,
                apiFailure = devices
            )
        }

        val heartbeatResponse = (heartbeat as ApiResult.Success).value
        val deviceResponses = (devices as ApiResult.Success).value
        val currentDevice = deviceResponses.firstOrNull {
            it.id == bootstrapResponse.deviceId
        }

        return BackendSessionStartupResult.Success(
            BackendSessionSnapshot(
                userId = bootstrapResponse.userId,
                deviceId = bootstrapResponse.deviceId,
                deviceName = currentDevice?.deviceName ?: request.deviceName,
                displayName = bootstrapResponse.displayName,
                lastHeartbeatAt = heartbeatResponse.lastSeenAt,
                deviceCount = deviceResponses.size
            )
        )
    }

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

    private suspend fun ensureAuthentication(
        storedSession: BackendSession
    ): ApiResult.Failure? {
        if (!storedSession.accessToken.isNullOrBlank() && !storedSession.userId.isNullOrBlank()) {
            return null
        }

        val result = if (storedSession.userId.isNullOrBlank()) {
            devRegister()
        } else {
            devLogin(storedSession.userId)
        }

        return result as? ApiResult.Failure
    }

    private suspend fun getCurrentUserWithDevRecovery(): ApiResult<MeResponse> {
        val initialResult = getCurrentUser()
        if (initialResult !is ApiResult.Failure || initialResult.statusCode != HTTP_UNAUTHORIZED) {
            return initialResult
        }

        val userId = sessionStore.load().userId
            ?: return initialResult

        val loginResult = devLogin(userId)
        if (loginResult is ApiResult.Failure) {
            return ApiResult.Failure(
                statusCode = loginResult.statusCode,
                error = loginResult.error,
                cause = loginResult.cause
            )
        }

        return getCurrentUser()
    }

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

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
