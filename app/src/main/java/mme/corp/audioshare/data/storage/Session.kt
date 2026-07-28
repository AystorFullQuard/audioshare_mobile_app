package mme.corp.audioshare.data.storage

data class Session(
    val accessToken: String?,

    val refreshToken: String?,

    val userId: String?,

    val sessionId: String?,

    val deviceId: String?
)