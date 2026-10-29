package mme.corp.audioshare.data.api

import mme.corp.audioshare.data.dto.call.CallSnapshot
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** V1 call control; does not transport microphone, camera, or raw media. */
interface CallsApi {

    @POST("/api/v1/rooms/{roomId}/calls")
    suspend fun start(@Path("roomId") roomId: String): Response<CallSnapshot>

    @GET("/api/v1/rooms/{roomId}/calls/current")
    suspend fun current(@Path("roomId") roomId: String): Response<CallSnapshot>

    @GET("/api/v1/calls/{callId}")
    suspend fun get(@Path("callId") callId: String): Response<CallSnapshot>

    @POST("/api/v1/calls/{callId}/join")
    suspend fun join(@Path("callId") callId: String): Response<CallSnapshot>

    @POST("/api/v1/calls/{callId}/leave")
    suspend fun leave(@Path("callId") callId: String): Response<CallSnapshot>

    @POST("/api/v1/calls/{callId}/end")
    suspend fun end(@Path("callId") callId: String): Response<CallSnapshot>
}
