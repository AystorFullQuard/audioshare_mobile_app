package mme.corp.audioshare.data.entity

/**
 * Non-authoritative client session summary; NOT ServerRelay's AuthSessionEntity/JPA row.
 * The server does not expose session row IDs, token hashes, family IDs, or rotation history.
 * Actual access/refresh credentials remain exclusively in the existing protected token store.
 * This summary is NOT an offline-host entitlement or proof of current cloud authorization.
 */
data class AuthSessionEntity(
    val userId: String? = null,
    val deviceId: String? = null,
    val authenticated: Boolean,
    val needsLogin: Boolean,
    val guestAllowed: Boolean = false,
    val refreshExpiresAt: String? = null
)
