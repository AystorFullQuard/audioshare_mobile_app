package mme.corp.audioshare.data.api

import mme.corp.audioshare.data.dto.room.CreateRoomRequest
import mme.corp.audioshare.data.dto.room.RoomDeviceActionRequest
import mme.corp.audioshare.data.dto.room.RoomMemberResponse
import mme.corp.audioshare.data.dto.room.RoomResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface RoomsApi {

    @POST("/api/v1/rooms")
    suspend fun createRoom(
        @Body request: CreateRoomRequest
    ): Response<RoomResponse>

    @GET("/api/v1/rooms")
    suspend fun getCurrentUserRooms(): Response<List<RoomResponse>>

    @GET("/api/v1/rooms/{roomId}")
    suspend fun getRoom(
        @Path("roomId") roomId: String
    ): Response<RoomResponse>

    @GET("/api/v1/rooms/{roomId}/members")
    suspend fun getActiveMembers(
        @Path("roomId") roomId: String
    ): Response<List<RoomMemberResponse>>

    @POST("/api/v1/rooms/{roomId}/join")
    suspend fun joinRoom(
        @Path("roomId") roomId: String,
        @Body request: RoomDeviceActionRequest
    ): Response<RoomResponse>

    @POST("/api/v1/rooms/{roomId}/leave")
    suspend fun leaveRoom(
        @Path("roomId") roomId: String,
        @Body request: RoomDeviceActionRequest
    ): Response<RoomResponse>

    @POST("/api/v1/rooms/{roomId}/archive")
    suspend fun archiveRoom(
        @Path("roomId") roomId: String,
        @Body request: RoomDeviceActionRequest
    ): Response<RoomResponse>
}
