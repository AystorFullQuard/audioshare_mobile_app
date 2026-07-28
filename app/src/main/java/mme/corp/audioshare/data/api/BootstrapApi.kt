package mme.corp.audioshare.data.api

import mme.corp.audioshare.data.dto.bootstrap.BootstrapRequest
import mme.corp.audioshare.data.dto.bootstrap.BootstrapResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface BootstrapApi {

    @POST("/api/v1/bootstrap")
    suspend fun bootstrap(
        @Body request: BootstrapRequest
    ): Response<BootstrapResponse>
}
