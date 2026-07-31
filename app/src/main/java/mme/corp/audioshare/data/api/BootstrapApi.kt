package mme.corp.audioshare.data.api

import mme.corp.audioshare.data.dto.bootstrap.BootstrapRequest
import mme.corp.audioshare.data.dto.bootstrap.BootstrapResponse
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface BootstrapApi {

    @POST("/api/v1/bootstrap")
    suspend fun bootstrap(
        @Body request: BootstrapRequest
    ): Response<BootstrapResponse>

    @POST("/api/v1/session/bootstrap")
    suspend fun sessionBootstrap(
        @Body request: BootstrapRequest
    ): Response<SessionBootstrapResponse>
}
