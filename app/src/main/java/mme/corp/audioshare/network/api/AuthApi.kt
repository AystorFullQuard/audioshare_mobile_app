package mme.corp.audioshare.network.api

import mme.corp.audioshare.network.dto.LoginRequest
import mme.corp.audioshare.network.dto.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("/api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>
}