package mme.corp.audioshare.backend.presence

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PresenceApi {
    @POST("api/v1/presence/heartbeat")
    suspend fun heartbeat(
        @Body request: PresenceHeartbeatRequest
    ): Response<PresenceHeartbeatResponse>
}
