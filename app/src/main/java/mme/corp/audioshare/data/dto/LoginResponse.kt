package mme.corp.audioshare.data.dto

/**
 * Authentication response shared by login and refresh endpoints.
 * Mirrors ServeRelay's nested { session, user } contract.
 */
data class LoginResponse(
    val session: SessionDto,
    val user: UserDto
)
