package mme.corp.audioshare.data.dto.bootstrap

/** Matches ServerRelay SessionBootstrapRq. The backend binds the session via registration. */
data class SessionBootstrapRequest(
    val installationId: String,
    val displayName: String? = null,
    val deviceName: String? = null,
    val platformVersion: String? = null,
    val appVersion: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val capabilities: List<String>? = null
)
