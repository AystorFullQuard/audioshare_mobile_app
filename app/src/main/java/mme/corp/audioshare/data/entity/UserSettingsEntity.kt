package mme.corp.audioshare.data.entity

/** User-specific preferences. Dates are optional because CurrentUser.settings omits them. */
data class UserSettingsEntity(
    val userId: String,
    val theme: String? = null,
    val allowDiscovery: Boolean = false,
    val showLastSeen: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val preferredAudioQuality: AudioQuality? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
