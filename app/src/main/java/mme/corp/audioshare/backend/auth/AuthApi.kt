package mme.corp.audioshare.backend.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/dev-register")
    suspend fun devRegister(): Response<LoginResponse>

    @POST("api/v1/auth/dev-login")
    suspend fun devLogin(
        @Body request: DevLoginRequest
    ): Response<LoginResponse>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<MeResponse>
}
