package mme.corp.audioshare.data.repository

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.api.BootstrapApi
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapRequest
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.storage.InstallationIdStore
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.network.retrofit.executeApiCall

data class SessionBootstrapMetadata(
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

fun interface SessionBootstrapLoader {

    suspend fun loadSessionBootstrap(
        metadata: SessionBootstrapMetadata
    ): Result<SessionBootstrapResponse>
}

class BootstrapRepository(
    private val bootstrapApi: BootstrapApi,
    private val installationIdStore: InstallationIdStore,
    private val logger: AppLogger = AppLogger.NO_OP
) : SessionBootstrapLoader {

    override suspend fun loadSessionBootstrap(
        metadata: SessionBootstrapMetadata
    ): Result<SessionBootstrapResponse> {
        val installationId = readInstallationId().getOrElse { exception ->
            logFailure("Session bootstrap installation lookup failed", exception)
            return Result.failure(exception)
        }
        if (installationId.isBlank() || installationId.length > 128) {
            return Result.failure(IllegalArgumentException("Invalid installation identity"))
        }

        logger.debug(TAG, "Session bootstrap started")

        return executeApiCall(
            emptyBodyMessage = "Session bootstrap response body is empty"
        ) {
            bootstrapApi.sessionBootstrap(
                SessionBootstrapRequest(
                    installationId = installationId,
                    displayName = metadata.displayName.normalized(128),
                    deviceName = metadata.deviceName.normalized(128),
                    platformVersion = metadata.platformVersion.normalized(32),
                    appVersion = metadata.appVersion.normalized(64),
                    locale = metadata.locale.normalized(32),
                    timezone = metadata.timezone.normalized(64),
                    capabilities = metadata.capabilities?.map { capability ->
                        require(capability.isNotBlank() && capability.length <= 64) {
                            "Invalid bootstrap capability"
                        }
                        capability
                    }?.also { require(it.size <= 64) { "Too many bootstrap capabilities" } }
                )
            )
        }.mapCatching { response ->
            check(response.session?.authenticated == true && response.session?.needsLogin == false) {
                "Bootstrap did not return an authenticated session"
            }
            check(!response.user?.id.isNullOrBlank()) {
                "Bootstrap user is missing"
            }
            check(response.presence?.userId == response.user?.id) {
                "Bootstrap presence user does not match session user"
            }
            check(response.devices.count { it.current && it.id.isNotBlank() } == 1) {
                "Bootstrap current device is missing or ambiguous"
            }
            response
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

    private suspend fun readInstallationId(): Result<String> = try {
        Result.success(installationIdStore.getOrCreateInstallationId())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private fun String?.normalized(maxLength: Int): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.length <= maxLength) { "Bootstrap metadata exceeds server limit" }
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
