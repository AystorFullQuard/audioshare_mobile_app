package mme.corp.audioshare.data.model.room

import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomMemberResponse
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomMemberState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomMappingsTest {

    @Test
    fun mapsMembershipAndPresenceAsIndependentStates() {
        val response = RoomMemberResponse(
            id = "membership-id",
            roomId = "room-id",
            userId = "user-id",
            username = "member",
            displayName = "Member Device",
            avatarURL = null,
            role = RoomMemberRole.MEMBER,
            state = RoomMemberState.ACTIVE,
            presenceState = PresenceState.OFFLINE,
            presenceLastSeenAt = null,
            joinedAt = "2026-07-31T12:00:00",
            membershipLastSeenAt = "2026-07-31T12:30:00",
            leftAt = null
        )

        val member = response.toDomain()

        assertEquals(RoomMemberState.ACTIVE, member.membershipState)
        assertEquals(PresenceState.OFFLINE, member.presenceState)
        assertEquals("2026-07-31T12:30:00", member.membershipLastSeenAt)
        assertNull(member.presenceLastSeenAt)
    }
}
