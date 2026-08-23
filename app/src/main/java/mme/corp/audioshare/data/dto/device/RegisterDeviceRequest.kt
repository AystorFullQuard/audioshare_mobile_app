package mme.corp.audioshare.data.dto.device

import mme.corp.audioshare.data.dto.bootstrap.Platform

data class RegisterDeviceRequest(
    val deviceName: String,
    val platform: Platform,
    val appVersion: String?,
    val manufacturer: String,
    val model: String,
    val platformVersion: String?
)
