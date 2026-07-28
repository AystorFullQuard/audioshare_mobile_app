package mme.corp.audioshare.data.api

import mme.corp.audioshare.data.dto.presence.PresenceHeartbeatRequest
import mme.corp.audioshare.data.dto.presence.PresenceHeartbeatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PresenceApi {

    @POST("/api/v1/presence/heartbeat")
    suspend fun heartbeat(
        @Body request: PresenceHeartbeatRequest
    ): Response<PresenceHeartbeatResponse>
}
