package mme.corp.audioshare.data.dto

data class LoginResponse(
    val userId: String,
    val sessionId: String,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)