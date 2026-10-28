package mme.corp.audioshare.data.dto.presence

import mme.corp.audioshare.data.entity.PresenceActivity
import mme.corp.audioshare.data.entity.PresenceAvailability
import mme.corp.audioshare.data.entity.PresencePreference

/** ServerRelay PresenceHeartbeatRs: three independent server-authoritative dimensions. */
data class PresenceHeartbeatResponse(
    val userId: String,
    val deviceId: String,
    val availability: PresenceAvailability,
    val activity: PresenceActivity,
    val preference: PresencePreference,
    val currentRoomId: String? = null,
    val lastSeenAt: String
)
