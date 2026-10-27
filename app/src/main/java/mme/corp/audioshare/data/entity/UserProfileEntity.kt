package mme.corp.audioshare.data.entity

/** Optional user-facing profile; server CurrentUser currently exposes only a subset. */
data class UserProfileEntity(
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
