package mme.corp.audioshare.data.dto

data class UserSettingsDto(

    val theme: String,

    val allowDiscovery: Boolean,

    val showLastSeen: Boolean,

    val notificationsEnabled: Boolean,

    val preferredAudioQuality: String
)