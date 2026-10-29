package mme.corp.audioshare.data.api

import mme.corp.audioshare.data.dto.invite.CreateRoomInviteRequest
import mme.corp.audioshare.data.dto.invite.RoomInviteResponse
import mme.corp.audioshare.data.dto.invite.RoomInviteSummaryResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/** Backend V1 invite control. JWT/CSRF applied by the shared Retrofit client. */
interface InvitesApi {
    @POST("/api/v1/rooms/{roomId}/invites")
    suspend fun create(
        @Path("roomId") roomId: String,
        @Body body: CreateRoomInviteRequest
    ): Response<RoomInviteResponse>

    @GET("/api/v1/rooms/{roomId}/invites")
    suspend fun list(@Path("roomId") roomId: String): Response<List<RoomInviteSummaryResponse>>

    @POST("/api/v1/rooms/{roomId}/invites/qr")
    @Headers("Accept: image/png")
    suspend fun createQr(
        @Path("roomId") roomId: String,
        @Body body: CreateRoomInviteRequest
    ): Response<ResponseBody>

    @DELETE("/api/v1/rooms/{roomId}/invites/{inviteId}")
    suspend fun revoke(
        @Path("roomId") roomId: String,
        @Path("inviteId") inviteId: String
    ): Response<Unit>
}
