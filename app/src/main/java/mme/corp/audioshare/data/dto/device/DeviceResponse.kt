package mme.corp.audioshare.data.dto.device

import mme.corp.audioshare.data.dto.bootstrap.Platform

data class DeviceResponse(
    val deviceId: String,
    val userId: String,
    val deviceName: String,
    val platform: Platform,
    val appVersion: String?,
    val lastSeenAt: String?,
    val createdAt: String?
)
