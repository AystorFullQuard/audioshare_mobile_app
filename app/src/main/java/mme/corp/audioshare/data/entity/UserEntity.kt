package mme.corp.audioshare.data.entity

/**
 * Client-safe, immutable projection of ServerRelay's UserEntity.
 * A user's profile, preferences, presence and devices are separate ID-linked snapshots.
 * Credential hashes, lockout counters and internal suspension details belong only to the server.
 * Nullable properties are NOT evidence that the backend has returned those values.
 */
data class UserEntity(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    val status: UserStatus? = null,
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
