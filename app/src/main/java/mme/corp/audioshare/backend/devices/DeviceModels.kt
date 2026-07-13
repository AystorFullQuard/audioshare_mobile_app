package mme.corp.audioshare.backend.devices

import kotlinx.serialization.Serializable
import mme.corp.audioshare.backend.bootstrap.Platform

@Serializable
data class DeviceResponse(
    val id: String,
    val deviceName: String? = null,
    val platform: Platform,
    val appVersion: String? = null,
    val lastSeenAt: String? = null,
    val createdAt: String
)
