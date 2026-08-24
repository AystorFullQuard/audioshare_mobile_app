package mme.corp.audioshare.data.api

import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapRequest
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

fun interface BootstrapApi {

    @POST("/api/v1/session/bootstrap")
    suspend fun sessionBootstrap(
        @Body request: SessionBootstrapRequest
    ): Response<SessionBootstrapResponse>
}
