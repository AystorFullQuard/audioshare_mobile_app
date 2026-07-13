package mme.corp.audioshare.backend.rooms

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface RoomsApi {
    @POST("api/v1/rooms")
    suspend fun createRoom(
        @Body request: CreateRoomRequest
    ): Response<RoomResponse>

    @GET("api/v1/rooms")
    suspend fun getRooms(): Response<List<RoomResponse>>

    @POST("api/v1/rooms/join-by-code")
    suspend fun joinByCode(
        @Body request: JoinRoomByCodeRequest
    ): Response<RoomResponse>

    @GET("api/v1/rooms/{roomId}")
    suspend fun getRoom(
        @Path("roomId") roomId: String
    ): Response<RoomResponse>

    @GET("api/v1/rooms/{roomId}/members")
    suspend fun getMembers(
        @Path("roomId") roomId: String
    ): Response<List<RoomMemberResponse>>

    @POST("api/v1/rooms/{roomId}/join")
    suspend fun joinRoom(
        @Path("roomId") roomId: String,
        @Body request: RoomDeviceActionRequest
    ): Response<RoomResponse>

    @POST("api/v1/rooms/{roomId}/leave")
    suspend fun leaveRoom(
        @Path("roomId") roomId: String,
        @Body request: RoomDeviceActionRequest
    ): Response<RoomResponse>

    @POST("api/v1/rooms/{roomId}/archive")
    suspend fun archiveRoom(
        @Path("roomId") roomId: String,
        @Body request: RoomDeviceActionRequest
    ): Response<RoomResponse>

    @POST("api/v1/rooms/{roomId}/invites")
    suspend fun createInvite(
        @Path("roomId") roomId: String,
        @Body request: CreateRoomInviteRequest
    ): Response<RoomInviteResponse>
}
