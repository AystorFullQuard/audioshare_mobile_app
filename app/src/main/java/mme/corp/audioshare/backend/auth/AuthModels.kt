package mme.corp.audioshare.backend.auth

import kotlinx.serialization.Serializable

@Serializable
data class DevLoginRequest(
    val userId: String
)

@Serializable
data class LoginResponse(
    val userId: String,
    val sessionId: String,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class MeResponse(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarURL: String? = null,
    val emailVerified: Boolean,
    val phoneVerified: Boolean,
    val settings: UserSettingsResponse
)

@Serializable
data class UserSettingsResponse(
    val theme: String,
    val allowDiscovery: Boolean,
    val showLastSeen: Boolean,
    val notificationsEnabled: Boolean,
    val preferredAudioQuality: AudioQuality
)

@Serializable
enum class AudioQuality {
    LOW_LATENCY,
    BALANCED_LATENCY,
    HIGH_LATENCY
}
