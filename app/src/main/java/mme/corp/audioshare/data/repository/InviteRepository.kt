package mme.corp.audioshare.data.repository

import mme.corp.audioshare.data.api.InvitesApi
import mme.corp.audioshare.data.dto.invite.CreateRoomInviteRequest
import mme.corp.audioshare.data.dto.invite.RoomInviteResponse
import mme.corp.audioshare.data.dto.invite.RoomInviteSummaryResponse
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.network.retrofit.executeApiCall
import mme.corp.audioshare.network.retrofit.executeApiCallWithoutBody

/** No UI state and no persistent raw invite codes; callers explicitly consume results. */
class InviteRepository(private val api: InvitesApi, private val devices: DeviceIdStore) {
    suspend fun create(
        roomId: String,
        expiresInMinutes: Int? = null,
        maxUses: Int? = null
    ): Result<RoomInviteResponse> =
        withDevice { deviceId ->
            executeApiCall(
                "Missing invite",
                {
                    api.create(
                        roomId,
                        CreateRoomInviteRequest(deviceId, expiresInMinutes, maxUses)
                    )
                })
        }

    suspend fun list(roomId: String): Result<List<RoomInviteSummaryResponse>> =
        executeApiCall("Missing invite summaries") { api.list(roomId) }

    suspend fun qr(
        roomId: String,
        expiresInMinutes: Int? = null,
        maxUses: Int? = null
    ): Result<ByteArray> =
        withDevice { deviceId ->
            executeApiCall("Missing QR image") {
                api.createQr(
                    roomId,
                    CreateRoomInviteRequest(deviceId, expiresInMinutes, maxUses)
                )
            }
                .mapCatching { body ->
                    body.use {
                        it.bytes().also { bytes -> require(bytes.size in 1..262144) }
                    }
                }
        }

    suspend fun revoke(roomId: String, inviteId: String): Result<Unit> =
        executeApiCallWithoutBody { api.revoke(roomId, inviteId) }

    private suspend fun <T> withDevice(block: suspend (String) -> Result<T>): Result<T> =
        devices.getDeviceId()?.takeIf(String::isNotBlank)?.let { block(it) }
            ?: Result.failure(IllegalStateException("Device registration is required"))
}
