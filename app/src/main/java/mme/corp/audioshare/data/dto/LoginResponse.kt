package mme.corp.audioshare.data.dto

/**
 * Authentication response contract shared by login and refresh endpoints.
 *
 * Matches ServeRelay's current flat LoginResponse payload.
 */
data class LoginResponse(
    val userId: String,
    val sessionId: String,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)
