package mme.corp.audioshare.data.dto

data class UserDto(

    val id: String,

    val email: String?,

    val phone: String?,

    val username: String,

    val displayName: String,

    val avatarURL: String?,

    val emailVerified: Boolean,

    val phoneVerified: Boolean,

    val settings: UserSettingsDto?
)