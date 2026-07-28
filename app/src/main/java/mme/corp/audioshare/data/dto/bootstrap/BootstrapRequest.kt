package mme.corp.audioshare.data.dto.bootstrap

data class BootstrapRequest(
    val deviceId: String? = null,
    val displayName: String? = null,
    val deviceName: String? = null,
    val platform: Platform = Platform.ANDROID,
    val appVersion: String? = null
)
