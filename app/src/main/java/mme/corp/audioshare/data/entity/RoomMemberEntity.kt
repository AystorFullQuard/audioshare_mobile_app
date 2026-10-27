package mme.corp.audioshare.data.entity

/** Persistent user-scoped room membership. HOST/RECEIVER are transient call roles. */
data class RoomMemberEntity(
    val id: String,
    val roomId: String,
    val userId: String,
    val role: RoomMemberRole,
    val state: RoomMemberState,
    val joinedAt: String,
    val lastSeenAt: String,
    val leftAt: String? = null
)
