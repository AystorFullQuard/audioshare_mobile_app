package mme.corp.audioshare.data.dto.bootstrap

import mme.corp.audioshare.data.dto.presence.PresenceState

data class BootstrapResponse(
    val userId: String,
    val deviceId: String,
    val displayName: String? = null,
    val presenceState: PresenceState
)
