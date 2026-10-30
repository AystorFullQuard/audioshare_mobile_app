package mme.corp.audioshare.data.repository

import mme.corp.audioshare.data.api.InvitesApi
import mme.corp.audioshare.data.dto.invite.CreateRoomInviteRequest
import mme.corp.audioshare.data.dto.invite.RoomInviteResponse
import mme.corp.audioshare.data.dto.invite.RoomInviteSummaryResponse
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.network.retrofit.executeApiCall
import mme.corp.audioshare.network.retrofit.executeApiCallWithoutBody
import okhttp3.ResponseBody
import java.util.UUID

/** No UI state and no persistent raw invite codes; callers explicitly consume results. */
class InviteRepository(private val api: InvitesApi, private val devices: DeviceIdStore) {
    suspend fun create(
        roomId: String,
        expiresInMinutes: Int? = null,
        maxUses: Int? = null
    ): Result<RoomInviteResponse> =
        withRoom(roomId) {
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
        }

    suspend fun list(roomId: String): Result<List<RoomInviteSummaryResponse>> =
        withRoom(roomId) { executeApiCall("Missing invite summaries") { api.list(roomId) } }

    suspend fun qr(
        roomId: String,
        expiresInMinutes: Int? = null,
        maxUses: Int? = null
    ): Result<ByteArray> =
        withRoom(roomId) {
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
        }

    suspend fun revoke(roomId: String, inviteId: String): Result<Unit> =
        withRoom(roomId) {
            withRoom(inviteId) {
                executeApiCallWithoutBody {
                    api.revoke(
                        roomId,
                        inviteId
                    )
                }
            }
        }

    private suspend fun <T> withRoom(id: String, block: suspend () -> Result<T>): Result<T> =
        if (runCatching {
                UUID.fromString(id).toString().equals(id, true)
            }.getOrDefault(false)) block()
        else Result.failure(IllegalArgumentException("Expected a ServerRelay UUID"))

    private suspend fun <T> withDevice(block: suspend (String) -> Result<T>): Result<T> =
        devices.getDeviceId()?.takeIf {
            runCatching { UUID.fromString(it).toString().equals(it, true) }.getOrDefault(false)
        }?.let { block(it) }
            ?: Result.failure(IllegalStateException("Device registration is required"))
}
