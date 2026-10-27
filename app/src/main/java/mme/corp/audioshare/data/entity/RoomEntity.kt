package mme.corp.audioshare.data.entity

/**
 * Persistent room metadata, not a call, capture session, or local offline peer session.
 * User and device owners are independent foreign-key identities.
 */
data class RoomEntity(
    val id: String,
    val ownerUserId: String,
    val ownerDeviceId: String,
    val name: String? = null,
    val status: RoomStatus,
    val visibility: RoomVisibility,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String? = null
)
