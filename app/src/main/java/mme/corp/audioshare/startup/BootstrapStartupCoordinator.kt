package mme.corp.audioshare.startup

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.dto.bootstrap.BootstrapResponse
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.repository.DeviceBootstrapper
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.presence.PresenceHeartbeatCoordinator
import mme.corp.audioshare.presence.PresenceRuntimeController

data class BootstrapStartupRequest(
    val displayName: String? = null,
    val deviceName: String? = null,
    val appVersion: String? = null,
    val platform: Platform = Platform.ANDROID
)

data class BootstrapStartupSnapshot(
    val bootstrap: BootstrapResponse,
    val presence: PresenceSnapshot
)

class BootstrapStartupCoordinator(
    private val deviceBootstrapper: DeviceBootstrapper,
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

        val bootstrap = deviceBootstrapper.bootstrap(
            displayName = request.displayName,
            deviceName = request.deviceName,
            appVersion = request.appVersion,
            platform = request.platform
        ).getOrElse { exception ->
            logFailure("Device bootstrap failed", exception)
            return Result.failure(exception)
        }

        logger.info(
            TAG,
            "Device bootstrap succeeded; " +
                "presenceState=${bootstrap.presenceState.name}"
        )

        logger.debug(TAG, "Sending initial presence heartbeat")

        val presence = try {
            heartbeatCoordinator.heartbeatNow()
        } catch (exception: CancellationException) {
            logger.debug(TAG, "Startup initial heartbeat cancelled")
            throw exception
        }.getOrElse { exception ->
            logFailure("Initial presence heartbeat failed", exception)
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
                bootstrap = bootstrap,
                presence = presence
            )
        )
    }

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

    private companion object {
        const val TAG = "PresenceStartup"
    }
}
