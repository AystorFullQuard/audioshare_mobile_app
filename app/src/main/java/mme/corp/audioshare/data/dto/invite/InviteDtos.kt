package mme.corp.audioshare.data.dto.invite

/** deviceId is the server-issued, authenticated device UUID, NOT installationId. */
data class CreateRoomInviteRequest(
    val deviceId: String,
    val expiresInMinutes: Int? = null,
    val maxUses: Int? = null
) {
    init {
        require(deviceId.isNotBlank())
        require(expiresInMinutes == null || expiresInMinutes in 1..1440)
        require(maxUses == null || maxUses in 1..100)
    }
}

data class RoomInviteResponse(
    val id: String,
    val roomId: String,
    val inviteCode: String,
    val maxUses: Int,
    val usedCount: Int,
    val expiresAt: String,
    val createdAt: String
)

data class RoomInviteSummaryResponse(
    val inviteId: String,
    val roomId: String,
    val maxUses: Int,
    val usedCount: Int,
    val remainingUses: Int,
    val expiresAt: String,
    val createdAt: String
)
