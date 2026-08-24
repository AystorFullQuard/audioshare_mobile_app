package mme.corp.audioshare.startup

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.repository.DeviceRepository
import mme.corp.audioshare.data.repository.SessionBootstrapMetadata
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.logging.AppLogger

interface DeviceRegistrationResolver {
    suspend fun resolveOrRegister(
        metadata: SessionBootstrapMetadata
    ): Result<String>

    suspend fun recoverRegistration(
        metadata: SessionBootstrapMetadata
    ): Result<String>
}

class DeviceRegistrationCoordinator(
    private val deviceRepository: DeviceRepository,
    private val deviceIdStore: DeviceIdStore,
    private val logger: AppLogger = AppLogger.NO_OP
) : DeviceRegistrationResolver {

    override suspend fun resolveOrRegister(
        metadata: SessionBootstrapMetadata
    ): Result<String> {
        val existingDeviceId = readDeviceId().getOrElse { exception ->
            logFailure("Device lookup failed", exception)
            return Result.failure(exception)
        }?.trim()?.takeIf(String::isNotEmpty)

        if (existingDeviceId != null) {
            logger.debug(TAG, "Using persisted registered device")
            return Result.success(existingDeviceId)
        }

        logger.info(
            TAG,
            "No persisted device; registering current installation"
        )

        return registerAndPersist(metadata)
    }

    override suspend fun recoverRegistration(
        metadata: SessionBootstrapMetadata
    ): Result<String> {
        logger.warn(
            TAG,
            "Replacing stale persisted device registration"
        )

        clearDeviceId().getOrElse { exception ->
            logFailure("Stale device invalidation failed", exception)
            return Result.failure(exception)
        }

        return registerAndPersist(metadata)
    }

    private suspend fun registerAndPersist(
        metadata: SessionBootstrapMetadata
    ): Result<String> {
        val registeredDevice = deviceRepository.registerDevice(
            deviceName = metadata.deviceName.orEmpty(),
            manufacturer = metadata.manufacturer.orEmpty(),
            model = metadata.model.orEmpty(),
            platform = metadata.platform,
            appVersion = metadata.appVersion,
            platformVersion = metadata.platformVersion
        ).getOrElse { exception ->
            logFailure("Device registration failed", exception)
            return Result.failure(exception)
        }

        return persistRegisteredDevice(registeredDevice.deviceId)
    }

    private suspend fun persistRegisteredDevice(
        deviceId: String
    ): Result<String> = try {
        deviceIdStore.saveDeviceId(deviceId)
        logger.info(TAG, "Registered device persisted")
        Result.success(deviceId)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        logFailure("Registered device persistence failed", exception)
        Result.failure(exception)
    }

    private suspend fun readDeviceId(): Result<String?> = try {
        Result.success(deviceIdStore.getDeviceId())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private suspend fun clearDeviceId(): Result<Unit> = try {
        deviceIdStore.clearDeviceId()
        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private fun logFailure(message: String, exception: Throwable) {
        logger.error(
            TAG,
            "$message; type=${exception.safeTypeName()}",
            exception
        )
    }

    private fun Throwable.safeTypeName(): String =
        this::class.java.simpleName.ifBlank { "Throwable" }

    private companion object {
        const val TAG = "DeviceRegistration"
    }
}
