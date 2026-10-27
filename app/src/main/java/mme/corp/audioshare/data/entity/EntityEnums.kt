package mme.corp.audioshare.data.entity

/** Values match ServerRelay's sv.re.entity.enums, not the older mobile PresenceState DTO. */
enum class UserStatus { ACTIVE, INACTIVE, DELETED, SUSPENDED }
enum class DeviceStatus { ACTIVE, UNLINKED }
enum class DevicePlatform { ANDROID, IOS, DESKTOP, WEB }
enum class AudioQuality { LOW_LATENCY, BALANCED_LATENCY, HIGH_LATENCY }
enum class RoomStatus { ACTIVE, ARCHIVED }
enum class RoomVisibility { PRIVATE, LOCAL_DISCOVERY;

    /** This controls discovery, not whether an invite can be used. */
    fun allowsOutsiderDirectAdmission(): Boolean = this == LOCAL_DISCOVERY
    fun supportsInviteAdmission(): Boolean = true
}
enum class RoomMemberRole { OWNER, MEMBER }
enum class RoomMemberState { ACTIVE, LEFT }
enum class PresenceAvailability { ONLINE, OFFLINE }
enum class PresenceActivity { NONE, IN_ROOM, STREAMING, WATCHING }
enum class PresencePreference { AUTO, DO_NOT_DISTURB, INVISIBLE }
