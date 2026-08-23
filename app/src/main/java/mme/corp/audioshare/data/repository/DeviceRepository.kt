package mme.corp.audioshare.data.repository

import mme.corp.audioshare.data.api.DevicesApi
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.device.DeviceResponse
import mme.corp.audioshare.data.dto.device.RegisterDeviceRequest
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.network.retrofit.executeApiCall

class DeviceRepository(
    private val devicesApi: DevicesApi,
    private val logger: AppLogger = AppLogger.NO_OP
) {

    suspend fun registerDevice(
        deviceName: String,
        manufacturer: String,
        model: String,
        platform: Platform = Platform.ANDROID,
        appVersion: String? = null,
        platformVersion: String? = null
    ): Result<DeviceResponse> {
        val request = buildRegisterDeviceRequest(
            deviceName = deviceName,
            manufacturer = manufacturer,
            model = model,
            platform = platform,
            appVersion = appVersion,
            platformVersion = platformVersion
        ).getOrElse { exception ->
            logFailure(exception)
            return Result.failure(exception)
        }

        logger.debug(
            TAG,
            "Device registration started; platform=${platform.name}, " +
                "hasAppVersion=${request.appVersion != null}"
        )

        return executeApiCall(
            emptyBodyMessage = "Device registration response body is empty"
        ) {
            devicesApi.registerDevice(request)
        }
            .mapCatching { response ->
                check(response.deviceId.isNotBlank()) {
                    "Device registration response device id is blank"
                }
                check(response.userId.isNotBlank()) {
                    "Device registration response user id is blank"
                }
                response
            }
            .onSuccess {
                logger.info(
                    TAG,
                    "Device registration succeeded; platform=${platform.name}"
                )
            }
            .onFailure { exception ->
                logFailure(exception)
            }
    }

    private fun logFailure(exception: Throwable) {
        if (exception is ApiException) {
            logger.warn(
                TAG,
                "Device registration failed; httpCode=${exception.httpCode}, " +
                    "apiCode=${exception.apiError.code}"
            )
        } else {
            logger.error(
                TAG,
                "Device registration failed; type=${exception.safeTypeName()}",
                exception
            )
        }
    }

    private fun buildRegisterDeviceRequest(
        deviceName: String,
        manufacturer: String,
        model: String,
        platform: Platform,
        appVersion: String?,
        platformVersion: String?
    ): Result<RegisterDeviceRequest> {
        val normalizedDeviceName = requireNotBlank(deviceName, "Device name")
            .getOrElse { return Result.failure(it) }
        val normalizedManufacturer = requireNotBlank(manufacturer, "Manufacturer")
            .getOrElse { return Result.failure(it) }
        val normalizedModel = requireNotBlank(model, "Model")
            .getOrElse { return Result.failure(it) }

        return Result.success(
            RegisterDeviceRequest(
                deviceName = normalizedDeviceName,
                platform = platform,
                appVersion = appVersion.normalizedOptional(),
                manufacturer = normalizedManufacturer,
                model = normalizedModel,
                platformVersion = platformVersion.normalizedOptional()
            )
        )
    }

    private fun requireNotBlank(
        value: String,
        fieldName: String
    ): Result<String> {
        val normalized = value.trim()
        return if (normalized.isEmpty()) {
            Result.failure(
                IllegalArgumentException("$fieldName must not be blank")
            )
        } else {
            Result.success(normalized)
        }
    }

    private fun String?.normalizedOptional(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)

    private fun Throwable.safeTypeName(): String =
        this::class.java.simpleName.ifBlank { "Throwable" }

    private companion object {
        const val TAG = "DeviceRepository"
    }
}
