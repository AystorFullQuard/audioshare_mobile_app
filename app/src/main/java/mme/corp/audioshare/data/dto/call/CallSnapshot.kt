package mme.corp.audioshare.data.dto.call

/** Snapshot fields follow ServerRelay's OpenAPI CallSnapshot V1. */
data class CallSnapshot(
    val id: String,
    val roomId: String,
    val startedByUserId: String,
    val startedByDeviceId: String,
    val status: String,
    val startedAt: String,
    val endedAt: String?,
    val participants: List<CallParticipantSnapshot>
)

data class CallParticipantSnapshot(
    val id: String,
    val userId: String,
    val deviceId: String,
    val role: String,
    val state: String,
    val joinedAt: String,
    val leftAt: String?
)
