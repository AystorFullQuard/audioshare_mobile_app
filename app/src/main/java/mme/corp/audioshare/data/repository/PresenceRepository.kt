package mme.corp.audioshare.data.repository

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.api.PresenceApi
import mme.corp.audioshare.data.dto.presence.PresenceHeartbeatRequest
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.exception.DeviceBootstrapRequiredException
import mme.corp.audioshare.network.retrofit.executeApiCall

interface PresenceHeartbeatClient {

    suspend fun heartbeat(
        state: PresenceState? = null
    ): Result<PresenceSnapshot>
}

class PresenceRepository(
    private val presenceApi: PresenceApi,
    private val deviceIdStore: DeviceIdStore
) : PresenceHeartbeatClient {

    override suspend fun heartbeat(
        state: PresenceState?
    ): Result<PresenceSnapshot> {
        val deviceId = try {
            deviceIdStore.getDeviceId()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return Result.failure(exception)
        }

        val resolvedDeviceId = deviceId
            ?.takeIf(String::isNotBlank)
            ?: return Result.failure(DeviceBootstrapRequiredException())

        return executeApiCall(
            emptyBodyMessage = "Presence heartbeat response body is empty"
        ) {
            presenceApi.heartbeat(
                PresenceHeartbeatRequest(
                    deviceId = resolvedDeviceId,
                    state = state
                )
            )
        }.map { response ->
            PresenceSnapshot(
                userId = response.userId,
                deviceId = response.deviceId,
                state = response.state,
                currentRoomId = response.currentRoomId,
                lastSeenAt = response.lastSeenAt
            )
        }
    }
}
