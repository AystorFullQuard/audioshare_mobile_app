package mme.corp.audioshare.startup

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.repository.SessionBootstrapLoader
import mme.corp.audioshare.data.repository.SessionBootstrapMetadata
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.presence.PresenceHeartbeatCoordinator
import mme.corp.audioshare.presence.PresenceRuntimeController
import mme.corp.audioshare.room.RoomSessionBootstrapRestorer
import mme.corp.audioshare.room.RoomSessionRuntimeController
import mme.corp.audioshare.room.RoomSessionState

data class BootstrapStartupRequest(
    val displayName: String? = null,
    val deviceName: String? = null,
    val appVersion: String? = null,
    val platform: Platform = Platform.ANDROID,
    val manufacturer: String? = null,
    val model: String? = null,
    val platformVersion: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val capabilities: List<String>? = null
)

data class BootstrapStartupSnapshot(
    val deviceId: String,
    val sessionBootstrap: SessionBootstrapResponse,
    val roomSession: RoomSessionState,
    val presence: PresenceSnapshot
)

class BootstrapStartupCoordinator(
    private val deviceRegistrationResolver: DeviceRegistrationResolver,
    private val sessionBootstrapLoader: SessionBootstrapLoader,
    private val roomSessionRestorer: RoomSessionBootstrapRestorer,
    private val roomSessionRuntimeController: RoomSessionRuntimeController,
    private val heartbeatCoordinator: PresenceHeartbeatCoordinator,
    private val presenceRuntimeController: PresenceRuntimeController,
    private val logger: AppLogger = AppLogger.NO_OP
) {

    suspend fun initialize(
        request: BootstrapStartupRequest
    ): Result<BootstrapStartupSnapshot> {
        logger.info(
            TAG,
            "Startup initialization started; platform=${request.platform.name}, " +
                "hasDeviceName=${!request.deviceName.isNullOrBlank()}, " +
                "hasAppVersion=${!request.appVersion.isNullOrBlank()}"
        )

        val metadata = request.toSessionBootstrapMetadata()

        val initialDeviceId = deviceRegistrationResolver.resolveOrRegister(metadata)
            .getOrElse { exception ->
                logFailure("Device resolution failed", exception)
                return Result.failure(exception)
            }

        logger.info(TAG, "Registered device resolved for startup")

        val bootstrapResolution = loadSessionBootstrapWithRecovery(
            metadata = metadata,
            initialDeviceId = initialDeviceId
        ).getOrElse { exception ->
            logFailure("Session bootstrap failed", exception)
            return Result.failure(exception)
        }

        val sessionBootstrap = bootstrapResolution.response

        logger.info(
            TAG,
            "Session bootstrap succeeded; roomCount=${sessionBootstrap.rooms.size}, " +
                "hasCurrentRoom=${sessionBootstrap.presence?.currentRoomId != null}"
        )

        val roomSession = try {
            roomSessionRestorer.restoreFromBootstrap(sessionBootstrap)
        } catch (exception: CancellationException) {
            logger.debug(TAG, "Room session restoration cancelled")
            throw exception
        }.getOrElse { exception ->
            logFailure("Room session restoration failed", exception)
            return Result.failure(exception)
        }

        logger.info(
            TAG,
            "Room session restoration succeeded; roomCount=${roomSession.rooms.size}, " +
                "hasCurrentRoom=${roomSession.currentRoom != null}, " +
                "memberCount=${roomSession.activeMembers.size}"
        )

        logger.debug(TAG, "Sending initial presence heartbeat")

        val presence = try {
            heartbeatCoordinator.heartbeatNow()
        } catch (exception: CancellationException) {
            roomSessionRuntimeController.resetAfterStartupFailure()
            logger.debug(TAG, "Startup initial heartbeat cancelled")
            throw exception
        }.getOrElse { exception ->
            roomSessionRuntimeController.resetAfterStartupFailure()
            logFailure("Initial presence heartbeat failed", exception)
            return Result.failure(exception)
        }

        if (roomSession.currentRoom?.id != presence.currentRoomId) {
            val exception = RoomPresenceMismatchException()
            roomSessionRuntimeController.resetAfterStartupFailure()
            logFailure(
                "Initial heartbeat disagreed with restored room session",
                exception
            )
            return Result.failure(exception)
        }

        logger.info(
            TAG,
            "Initial presence heartbeat succeeded; " +
                "state=${presence.state.name}, " +
                "hasCurrentRoom=${presence.currentRoomId != null}"
        )

        presenceRuntimeController.activateAfterInitialHeartbeat()

        logger.info(TAG, "Presence runtime activated after startup")

        return Result.success(
            BootstrapStartupSnapshot(
                deviceId = bootstrapResolution.deviceId,
                sessionBootstrap = sessionBootstrap,
                roomSession = roomSession,
                presence = presence
            )
        )
    }

    private suspend fun loadSessionBootstrapWithRecovery(
        metadata: SessionBootstrapMetadata,
        initialDeviceId: String
    ): Result<SessionBootstrapResolution> {
        val firstAttempt = sessionBootstrapLoader.loadSessionBootstrap(metadata)

        firstAttempt.getOrNull()?.let { response ->
            return Result.success(
                SessionBootstrapResolution(
                    deviceId = initialDeviceId,
                    response = response
                )
            )
        }

        val firstFailure = firstAttempt.exceptionOrNull()
            ?: return Result.failure(
                IllegalStateException("Session bootstrap failed without an exception")
            )

        if (!firstFailure.requiresDeviceRegistrationRecovery()) {
            return Result.failure(firstFailure)
        }

        logger.warn(
            TAG,
            "Session bootstrap rejected persisted device; attempting one recovery"
        )

        val recoveredDeviceId = deviceRegistrationResolver
            .recoverRegistration(metadata)
            .getOrElse { exception ->
                return Result.failure(exception)
            }

        return sessionBootstrapLoader.loadSessionBootstrap(metadata)
            .map { response ->
                SessionBootstrapResolution(
                    deviceId = recoveredDeviceId,
                    response = response
                )
            }
    }

    private fun Throwable.requiresDeviceRegistrationRecovery(): Boolean =
        this is ApiException && apiError.code in RECOVERABLE_DEVICE_ERROR_CODES

    private fun BootstrapStartupRequest.toSessionBootstrapMetadata() =
        SessionBootstrapMetadata(
            displayName = displayName,
            deviceName = deviceName,
            appVersion = appVersion,
            platform = platform,
            manufacturer = manufacturer,
            model = model,
            platformVersion = platformVersion,
            locale = locale,
            timezone = timezone,
            capabilities = capabilities
        )

    private fun logFailure(
        message: String,
        exception: Throwable
    ) {
        if (exception is ApiException) {
            logger.error(
                TAG,
                "$message; type=ApiException, " +
                    "httpCode=${exception.httpCode}, " +
                    "apiCode=${exception.apiError.code}"
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

    private data class SessionBootstrapResolution(
        val deviceId: String,
        val response: SessionBootstrapResponse
    )

    private class RoomPresenceMismatchException :
        IllegalStateException(
            "Room session changed during startup; retry is required"
        )

    private companion object {
        const val TAG = "BootstrapStartup"

        val RECOVERABLE_DEVICE_ERROR_CODES = setOf(
            "DEVICE_NOT_OWNED",
            "DEVICE_NOT_FOUND",
            "DEVICE_REGISTRATION_REQUIRED"
        )
    }
}
