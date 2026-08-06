package mme.corp.audioshare.data.dto

data class SessionDto(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String,
    val refreshTokenExpiresAt: String,
    val complete: Boolean
)