package mme.corp.audioshare.data.repository

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.api.BootstrapApi
import mme.corp.audioshare.data.dto.bootstrap.BootstrapRequest
import mme.corp.audioshare.data.dto.bootstrap.BootstrapResponse
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.exception.DeviceBootstrapRequiredException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.network.retrofit.executeApiCall

interface DeviceBootstrapper {

    suspend fun bootstrap(
        displayName: String?,
        deviceName: String?,
        appVersion: String?,
        platform: Platform = Platform.ANDROID
    ): Result<BootstrapResponse>
}

interface SessionBootstrapLoader {

    suspend fun loadSessionBootstrap(
        displayName: String?,
        deviceName: String?,
        appVersion: String?,
        platform: Platform = Platform.ANDROID
    ): Result<SessionBootstrapResponse>
}

class BootstrapRepository(
    private val bootstrapApi: BootstrapApi,
    private val deviceIdStore: DeviceIdStore,
    private val logger: AppLogger = AppLogger.NO_OP
) : DeviceBootstrapper, SessionBootstrapLoader {

    override suspend fun bootstrap(
        displayName: String?,
        deviceName: String?,
        appVersion: String?,
        platform: Platform
    ): Result<BootstrapResponse> {
        val existingDeviceId = readDeviceId().getOrElse { exception ->
            logFailure("Device bootstrap device lookup failed", exception)
            return Result.failure(exception)
        }

        val result = executeApiCall(
            emptyBodyMessage = "Bootstrap response body is empty"
        ) {
            bootstrapApi.bootstrap(
                BootstrapRequest(
                    deviceId = existingDeviceId,
                    displayName = displayName,
                    deviceName = deviceName,
                    platform = platform,
                    appVersion = appVersion
                )
            )
        }

        val response = result.getOrElse { exception ->
            logFailure("Device bootstrap failed", exception)
            return Result.failure(exception)
        }

        return try {
            deviceIdStore.saveDeviceId(response.deviceId)
            logger.debug(TAG, "Device bootstrap response persisted")
            Result.success(response)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logFailure("Device bootstrap persistence failed", exception)
            Result.failure(exception)
        }
    }

    override suspend fun loadSessionBootstrap(
        displayName: String?,
        deviceName: String?,
        appVersion: String?,
        platform: Platform
    ): Result<SessionBootstrapResponse> {
        val deviceId = readDeviceId().getOrElse { exception ->
            logFailure("Session bootstrap device lookup failed", exception)
            return Result.failure(exception)
        }?.trim()?.takeIf(String::isNotEmpty)

        if (deviceId == null) {
            val exception = DeviceBootstrapRequiredException()
            logFailure(
                "Session bootstrap rejected before network call",
                exception
            )
            return Result.failure(exception)
        }

        logger.debug(TAG, "Session bootstrap started")

        return executeApiCall(
            emptyBodyMessage = "Session bootstrap response body is empty"
        ) {
            bootstrapApi.sessionBootstrap(
                BootstrapRequest(
                    deviceId = deviceId,
                    displayName = displayName,
                    deviceName = deviceName,
                    platform = platform,
                    appVersion = appVersion
                )
            )
        }.onSuccess { response ->
            logger.info(
                TAG,
                "Session bootstrap succeeded; roomCount=${response.rooms.size}, " +
                    "hasCurrentRoom=${response.presence?.currentRoomId != null}"
            )
        }.onFailure { exception ->
            logFailure("Session bootstrap failed", exception)
        }
    }

    private suspend fun readDeviceId(): Result<String?> = try {
        Result.success(deviceIdStore.getDeviceId())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private fun logFailure(message: String, exception: Throwable) {
        if (exception is ApiException) {
            val summary = "$message; type=ApiException, " +
                "httpCode=${exception.httpCode}, apiCode=${exception.apiError.code}"
            if (exception.httpCode >= 500) {
                logger.error(TAG, summary)
            } else {
                logger.warn(TAG, summary)
            }
        } else if (exception is DeviceBootstrapRequiredException) {
            logger.warn(
                TAG,
                "$message; type=DeviceBootstrapRequiredException"
            )
        } else {
            logger.error(
                TAG,
                "$message; type=${exception.safeTypeName()}",
                exception
            )
        }
    }

    private fun Throwable.safeTypeName(): String =
        this::class.java.simpleName.ifBlank { "Throwable" }

    private companion object {
        const val TAG = "BootstrapRepository"
    }
}
