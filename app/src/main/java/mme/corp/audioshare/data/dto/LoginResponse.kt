package mme.corp.audioshare.data.dto

data class LoginResponse(
    val session: SessionDto,
    val user: UserDto
)