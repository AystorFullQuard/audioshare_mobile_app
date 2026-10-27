package mme.corp.audioshare.data.entity

/**
 * User-scoped presence with independent liveness, activity and privacy preference.
 * Server heartbeat determines availability; client cannot self-assert STREAMING activity.
 */
data class UserPresenceEntity(
    val userId: String,
    val availability: PresenceAvailability,
    val activity: PresenceActivity,
    val preference: PresencePreference,
    val currentRoomId: String? = null,
    val lastSeenAt: String? = null,
    val updatedAt: String? = null
)
