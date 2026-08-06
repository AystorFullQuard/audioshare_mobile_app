package mme.corp.audioshare.data.api

import mme.corp.audioshare.data.dto.LoginRequest
import mme.corp.audioshare.data.dto.LoginResponse
import mme.corp.audioshare.data.dto.RefreshRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("/api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): Response<LoginResponse>
}