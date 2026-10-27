package mme.corp.audioshare.data.entity

/**
 * Safe invite metadata. Invite codes are returned once by an API operation and should be
 * handled as a short-lived secret, NEVER embedded in a cached entity or logged.
 * The database's codeHash is not a client property.
 */
data class RoomInviteEntity(
    val id: String,
    val roomId: String,
    val createdByUserId: String? = null,
    val maxUses: Int,
    val usedCount: Int,
    val expiresAt: String,
    val createdAt: String,
    val updatedAt: String? = null,
    val revokedAt: String? = null
)
