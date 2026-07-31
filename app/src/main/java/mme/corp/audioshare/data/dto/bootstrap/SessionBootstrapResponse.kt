package mme.corp.audioshare.data.dto.bootstrap

data class SessionBootstrapResponse(
    val rooms: List<RoomSummaryResponse> = emptyList(),
    val presence: SessionPresenceResponse? = null
)
