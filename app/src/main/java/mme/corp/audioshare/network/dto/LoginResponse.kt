package mme.corp.audioshare.network.dto

data class LoginResponse(
    val userId: String,
    val sessionId: String,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)