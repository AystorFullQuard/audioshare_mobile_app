package mme.corp.audioshare.data.model.presence

import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.entity.PresenceActivity
import mme.corp.audioshare.data.entity.PresenceAvailability
import mme.corp.audioshare.data.entity.PresencePreference

/** Retains the legacy UI projection while preserving the complete server presence. */
data class PresenceSnapshot(
    val userId: String,
    val deviceId: String,
    val state: PresenceState,
    val currentRoomId: String?,
    val lastSeenAt: String,
    val availability: PresenceAvailability =
        if (state == PresenceState.OFFLINE) PresenceAvailability.OFFLINE else PresenceAvailability.ONLINE,
    val activity: PresenceActivity = when (state) {
        PresenceState.IN_ROOM -> PresenceActivity.IN_ROOM
        PresenceState.STREAMING -> PresenceActivity.STREAMING
        PresenceState.WATCHING -> PresenceActivity.WATCHING
        else -> PresenceActivity.NONE
    },
    val preference: PresencePreference = PresencePreference.AUTO
)
