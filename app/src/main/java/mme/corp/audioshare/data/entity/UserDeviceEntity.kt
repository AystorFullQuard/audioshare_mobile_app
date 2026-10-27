package mme.corp.audioshare.data.entity

/**
 * Server-registered device identity; [id] is the backend UUID as text, not [installationId].
 * Neither push token nor last IP address is mirrored into a general-purpose mobile entity.
 */
data class UserDeviceEntity(
    val id: String,
    val userId: String? = null,
    val installationId: String? = null,
    val deviceName: String? = null,
    val platform: DevicePlatform? = null,
    val appVersion: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val platformVersion: String? = null,
    val status: DeviceStatus? = null,
    val lastSeenAt: String? = null,
    val createdAt: String? = null,
    val unlinkedAt: String? = null
)
