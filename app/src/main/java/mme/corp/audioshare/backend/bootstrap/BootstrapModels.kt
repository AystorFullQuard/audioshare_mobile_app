package mme.corp.audioshare.backend.bootstrap

import kotlinx.serialization.Serializable

@Serializable
data class BootstrapRequest(
    val deviceId: String? = null,
    val displayName: String? = null,
    val deviceName: String? = null,
    val platform: Platform = Platform.ANDROID,
    val appVersion: String? = null
)

@Serializable
data class BootstrapResponse(
    val userId: String,
    val deviceId: String,
    val displayName: String? = null,
    val presenceState: PresenceState
)

@Serializable
enum class Platform {
    ANDROID,
    IOS,
    DESKTOP,
    WEB
}

@Serializable
enum class PresenceState {
    ONLINE,
    OFFLINE,
    IN_ROOM,
    STREAMING,
    WATCHING
}
