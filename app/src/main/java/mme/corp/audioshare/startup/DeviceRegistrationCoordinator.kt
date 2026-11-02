package mme.corp.audioshare.startup

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.repository.DeviceRepository
import mme.corp.audioshare.data.repository.SessionBootstrapMetadata
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.data.storage.InstallationIdStore
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
    private val installationIdStore: InstallationIdStore,
    private val logger: AppLogger = AppLogger.NO_OP
) : DeviceRegistrationResolver {

    override suspend fun resolveOrRegister(
        metadata: SessionBootstrapMetadata
    ): Result<String> {
        // Each login issues a NEW server session. A cached device UUID alone does not
        // bind that session. The backend's POST /devices/register is idempotent by
        // (userId, installationId): it reuses the existing ACTIVE device and binds
        // the current session without consuming an additional device slot.
        // Always send the same installation ID; never derive identity from IP/model.
        logger.debug(TAG, "Binding authenticated session to current installation")
        return registerAndPersist(metadata)
    }

    override suspend fun recoverRegistration(
        metadata: SessionBootstrapMetadata
    ): Result<String> {
        // Do not erase the saved device before recovery succeeds. A transient
        // failure or DEVICE_LIMIT_REACHED must not destroy a valid local mapping.
        logger.warn(TAG, "Retrying installation registration after bootstrap rejection")
        return registerAndPersist(metadata)
    }

    private suspend fun registerAndPersist(
        metadata: SessionBootstrapMetadata
    ): Result<String> {
        val installationId = try {
            installationIdStore.getOrCreateInstallationId()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logFailure("Installation identity lookup failed", exception)
            return Result.failure(exception)
        }

        val registeredDevice = deviceRepository.registerDevice(
            installationId = installationId,
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
