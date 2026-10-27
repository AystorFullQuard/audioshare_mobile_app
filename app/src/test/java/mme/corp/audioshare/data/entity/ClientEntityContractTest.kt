package mme.corp.audioshare.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Entity-only tests; Retrofit models/mappers and HTTP flows are deliberately out of scope. */
class ClientEntityContractTest {

    @Test
    fun `mobile enum values match server contract`() {
        assertEquals(
            setOf("ACTIVE", "INACTIVE", "DELETED", "SUSPENDED"),
            UserStatus.entries.map { it.name }.toSet()
        )
        assertEquals(setOf("ACTIVE", "UNLINKED"), DeviceStatus.entries.map { it.name }.toSet())
        assertEquals(
            setOf("ANDROID", "IOS", "DESKTOP", "WEB"),
            DevicePlatform.entries.map { it.name }.toSet()
        )
        assertEquals(
            setOf("LOW_LATENCY", "BALANCED_LATENCY", "HIGH_LATENCY"),
            AudioQuality.entries.map { it.name }.toSet()
        )
        assertEquals(setOf("ACTIVE", "ARCHIVED"), RoomStatus.entries.map { it.name }.toSet())
        assertEquals(
            setOf("PRIVATE", "LOCAL_DISCOVERY"),
            RoomVisibility.entries.map { it.name }.toSet()
        )
        assertEquals(setOf("OWNER", "MEMBER"), RoomMemberRole.entries.map { it.name }.toSet())
        assertEquals(setOf("ACTIVE", "LEFT"), RoomMemberState.entries.map { it.name }.toSet())
        assertEquals(
            setOf("ONLINE", "OFFLINE"),
            PresenceAvailability.entries.map { it.name }.toSet()
        )
        assertEquals(
            setOf("NONE", "IN_ROOM", "STREAMING", "WATCHING"),
            PresenceActivity.entries.map { it.name }.toSet()
        )
        assertEquals(
            setOf("AUTO", "DO_NOT_DISTURB", "INVISIBLE"),
            PresencePreference.entries.map { it.name }.toSet()
        )
    }

    @Test
    fun `room invitation is independent from public discovery`() {
        assertTrue(RoomVisibility.PRIVATE.supportsInviteAdmission())
        assertTrue(RoomVisibility.LOCAL_DISCOVERY.supportsInviteAdmission())
        assertFalse(RoomVisibility.PRIVATE.allowsOutsiderDirectAdmission())
        assertTrue(RoomVisibility.LOCAL_DISCOVERY.allowsOutsiderDirectAdmission())
    }

    @Test
    fun `presence liveness and activity are separate`() {
        val presence = UserPresenceEntity(
            userId = "u1",
            availability = PresenceAvailability.OFFLINE,
            activity = PresenceActivity.IN_ROOM,
            preference = PresencePreference.INVISIBLE,
            currentRoomId = "r1"
        )
        assertEquals(PresenceAvailability.OFFLINE, presence.availability)
        assertEquals(PresenceActivity.IN_ROOM, presence.activity)
        assertEquals("r1", presence.currentRoomId)
    }

    @Test
    fun `room membership belongs to user not device`() {
        val member = RoomMemberEntity(
            "m1",
            "r1",
            "u1",
            RoomMemberRole.MEMBER,
            RoomMemberState.ACTIVE,
            "2026-10-26T12:00:00",
            "2026-10-26T12:00:00"
        )
        assertEquals("u1", member.userId)
        assertEquals("r1", member.roomId)
    }

    @Test
    fun `device backend id and installation id stay distinct`() {
        val device =
            UserDeviceEntity(id = "server-device-uuid", installationId = "client-installation-id")
        assertFalse(device.id == device.installationId)
    }

    @Test
    fun `no credential hash or raw invite secret in client entity constructors`() {
        val properties = arrayOf(
            UserEntity::class.java,
            UserProfileEntity::class.java,
            UserSettingsEntity::class.java,
            UserDeviceEntity::class.java,
            AuthSessionEntity::class.java,
            RoomEntity::class.java,
            RoomMemberEntity::class.java,
            RoomInviteEntity::class.java,
            UserPresenceEntity::class.java
        ).flatMap { it.declaredFields.map { field -> field.name } }.map { it.lowercase() }
        listOf(
            "password", "refreshtoken", "accesstoken", "tokenhash", "codehash", "invitecode",
            "lastipaddress", "pushtoken", "lockeduntil", "familyid"
        ).forEach { secret ->
            assertFalse(
                "entity field must not expose $secret",
                properties.any { it.contains(secret) })
        }
    }
}
