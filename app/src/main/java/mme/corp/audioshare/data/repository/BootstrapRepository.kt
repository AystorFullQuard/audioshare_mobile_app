package mme.corp.audioshare.data.repository

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.api.BootstrapApi
import mme.corp.audioshare.data.dto.bootstrap.BootstrapRequest
import mme.corp.audioshare.data.dto.bootstrap.BootstrapResponse
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.network.retrofit.executeApiCall

interface DeviceBootstrapper {

    suspend fun bootstrap(
        displayName: String?,
        deviceName: String?,
        appVersion: String?,
        platform: Platform = Platform.ANDROID
    ): Result<BootstrapResponse>
}

class BootstrapRepository(
    private val bootstrapApi: BootstrapApi,
    private val deviceIdStore: DeviceIdStore
) : DeviceBootstrapper {

    override suspend fun bootstrap(
        displayName: String?,
        deviceName: String?,
        appVersion: String?,
        platform: Platform
    ): Result<BootstrapResponse> {
        val existingDeviceId = try {
            deviceIdStore.getDeviceId()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
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
            return Result.failure(exception)
        }

        return try {
            deviceIdStore.saveDeviceId(response.deviceId)
            Result.success(response)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
