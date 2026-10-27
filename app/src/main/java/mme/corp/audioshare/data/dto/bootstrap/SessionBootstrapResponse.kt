package mme.corp.audioshare.data.dto.bootstrap

import mme.corp.audioshare.data.dto.UserDto

/** Backend SessionBootstrapRs; unknown fields are ignored for forward compatibility. */
data class SessionBootstrapResponse(
    val session: BootstrapSession? = null,
    val user: UserDto? = null,
    val configuration: BootstrapConfiguration? = null,
    val capabilities: BootstrapCapabilities? = null,
    val devices: List<BootstrapDevice> = emptyList(),
    val rooms: List<RoomSummaryResponse> = emptyList(),
    val presence: SessionPresenceResponse? = null,
    val server: BootstrapServer? = null
)

/** Deliberately does not deserialize or duplicate tokens returned by the backend. */
class BootstrapSession(
    val authenticated: Boolean = false,
    val guestAllowed: Boolean = false,
    val needsLogin: Boolean = true
)

data class BootstrapDevice(
    val id: String,
    val current: Boolean,
    val deviceName: String,
    val platform: Platform,
    val appVersion: String?,
    val lastSeenAt: String?
)

data class BootstrapConfiguration(
    val heartbeatIntervalSeconds: Int,
    val presenceTimeoutSeconds: Int,
    val maxRoomMembers: Int,
    val maxRoomNameLength: Int,
    val udpPort: Int,
    val audioCodec: String?,
    val audioSampleRate: Int,
    val audioFrameDurationMs: Int,
    val maxAudioPacketSize: Int,
    val websocketEndpoint: String?,
    val apiVersion: String?,
    val supportsGuestLogin: Boolean
)

data class BootstrapCapabilities(
    val audio: Boolean,
    val rooms: Boolean,
    val presence: Boolean,
    val invites: Boolean,
    val websocket: Boolean,
    val pushNotifications: Boolean,
    val guestAuthentication: Boolean,
    val multiDevice: Boolean,
    val moderation: Boolean,
    val deviceManagement: Boolean,
    val fileTransfer: Boolean,
    val screenShare: Boolean
)

data class BootstrapServer(
    val name: String?,
    val version: String?,
    val build: String?,
    val environment: String?,
    val apiVersion: String?,
    val serverTime: String?,
    val maintenanceMode: Boolean
)
