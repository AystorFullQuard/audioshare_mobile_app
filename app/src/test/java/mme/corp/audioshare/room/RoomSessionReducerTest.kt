package mme.corp.audioshare.room

import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomMemberState
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.model.room.RoomMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSessionReducerTest {

    @Test
    fun roomDetailsMutationUpdatesSelectedRoomAndList() {
        val original = room("room-1", "Original")
        val updated = room("room-1", "Updated")
        val state = RoomSessionState(
            rooms = listOf(original),
            currentRoom = original
        )

        val result = RoomSessionReducer.reduce(
            state,
            RoomSessionMutation.RoomDetailsUpdated(updated)
        )

        assertEquals("Updated", result.currentRoom?.name)
        assertEquals("Updated", result.rooms.single().name)
    }

    @Test
    fun roomsReplacedPreservesSelectionWhenMissingAndClearingDisabled() {
        val selected = room("room-1", "Selected")
        val state = RoomSessionState(
            rooms = listOf(selected),
            currentRoom = selected
        )

        val result = RoomSessionReducer.reduce(
            state,
            RoomSessionMutation.RoomsReplaced(
                rooms = listOf(room("room-2", "Other")),
                clearSelectionIfMissing = false
            )
        )

        assertEquals("room-1", result.currentRoom?.id)
    }

    @Test
    fun roomsReplacedClearsMembersWhenSelectionDisappears() {
        val selected = room("room-1", "Selected")
        val state = RoomSessionState(
            rooms = listOf(selected),
            currentRoom = selected,
            activeMembers = listOf(member("room-1"))
        )

        val result = RoomSessionReducer.reduce(
            state,
            RoomSessionMutation.RoomsReplaced(
                rooms = listOf(room("room-2", "Other"))
            )
        )

        assertNull(result.currentRoom)
        assertTrue(result.activeMembers.isEmpty())
    }

    @Test
    fun roomActivatedSelectsRoomAndUpsertsMembershipSnapshot() {
        val existing = room("room-1", "Existing")
        val activated = room("room-2", "Activated")
        val members = listOf(member("room-2"))
        val state = RoomSessionState(
            rooms = listOf(existing),
            currentRoom = existing,
            activeMembers = listOf(member("room-1"))
        )

        val result = RoomSessionReducer.reduce(
            state,
            RoomSessionMutation.RoomActivated(
                room = activated,
                members = members
            )
        )

        assertEquals(listOf("room-1", "room-2"), result.rooms.map { it.id })
        assertEquals("room-2", result.currentRoom?.id)
        assertEquals(members, result.activeMembers)
    }

    @Test
    fun roomDeactivatedClearsContextButKeepsMembershipRoom() {
        val selected = room("room-1", "Selected")
        val state = RoomSessionState(
            rooms = listOf(selected),
            currentRoom = selected,
            activeMembers = listOf(member("room-1"))
        )

        val result = RoomSessionReducer.reduce(
            state,
            RoomSessionMutation.RoomDeactivated("room-1")
        )

        assertEquals(listOf("room-1"), result.rooms.map { it.id })
        assertNull(result.currentRoom)
        assertTrue(result.activeMembers.isEmpty())
    }

    @Test
    fun staleRoomDeactivatedDoesNotClearNewActiveRoom() {
        val oldRoom = room("room-1", "Old")
        val current = room("room-2", "Current")
        val members = listOf(member("room-2"))
        val state = RoomSessionState(
            rooms = listOf(oldRoom, current),
            currentRoom = current,
            activeMembers = members
        )

        val result = RoomSessionReducer.reduce(
            state,
            RoomSessionMutation.RoomDeactivated("room-1")
        )

        assertEquals("room-2", result.currentRoom?.id)
        assertEquals(members, result.activeMembers)
        assertEquals(listOf("room-1", "room-2"), result.rooms.map { it.id })
    }

    @Test
    fun roomRemovedMutationClearsOnlyMatchingSelection() {
        val selected = room("room-1", "Selected")
        val other = room("room-2", "Other")
        val state = RoomSessionState(
            rooms = listOf(selected, other),
            currentRoom = selected
        )

        val result = RoomSessionReducer.reduce(
            state,
            RoomSessionMutation.RoomRemoved("room-1")
        )

        assertNull(result.currentRoom)
        assertTrue(result.activeMembers.isEmpty())
        assertEquals(listOf("room-2"), result.rooms.map { it.id })
    }

    private fun member(roomId: String): RoomMember = RoomMember(
        membershipId = "membership-1",
        roomId = roomId,
        userId = "user-1",
        username = "user",
        displayName = "User",
        avatarURL = null,
        role = RoomMemberRole.MEMBER,
        membershipState = RoomMemberState.ACTIVE,
        presenceState = PresenceState.IN_ROOM,
        presenceLastSeenAt = "2026-07-31T12:00:00",
        joinedAt = "2026-07-31T12:00:00",
        membershipLastSeenAt = "2026-07-31T12:00:00",
        leftAt = null
    )

    private fun room(id: String, name: String): Room = Room(
        id = id,
        ownerUserId = "owner-1",
        ownerDeviceId = "device-1",
        name = name,
        status = RoomStatus.ACTIVE,
        visibility = RoomVisibility.PRIVATE,
        createdAt = "2026-07-31T12:00:00",
        updatedAt = "2026-07-31T12:00:00",
        archivedAt = null
    )
}
