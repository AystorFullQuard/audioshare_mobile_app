package mme.corp.audioshare.data.dto.bootstrap

data class SessionBootstrapRequest(
    val deviceId: String,
    val displayName: String? = null,
    val deviceName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val platform: Platform = Platform.ANDROID,
    val platformVersion: String? = null,
    val appVersion: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val capabilities: List<String>? = null
)
