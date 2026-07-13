package mme.corp.audioshare.backend.bootstrap

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface BootstrapApi {
    @POST("api/v1/bootstrap")
    suspend fun bootstrap(
        @Body request: BootstrapRequest
    ): Response<BootstrapResponse>
}
