package mme.corp.audioshare.data.dto.bootstrap

import mme.corp.audioshare.data.entity.PresenceActivity
import mme.corp.audioshare.data.entity.PresenceAvailability
import mme.corp.audioshare.data.entity.PresencePreference

/** ServerRelay's presence snapshot: liveness and activity are independent. */
data class SessionPresenceResponse(
    val userId: String,
    val availability: PresenceAvailability,
    val activity: PresenceActivity,
    val preference: PresencePreference,
    val currentRoomId: String? = null,
    val lastSeenAt: String,
    val updatedAt: String? = null
)
