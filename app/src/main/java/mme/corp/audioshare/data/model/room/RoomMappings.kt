package mme.corp.audioshare.data.model.room

import mme.corp.audioshare.data.dto.bootstrap.RoomSummaryResponse
import mme.corp.audioshare.data.dto.room.RoomMemberResponse
import mme.corp.audioshare.data.dto.room.RoomResponse

internal fun RoomResponse.toDomain(): Room = Room(
    id = id,
    ownerUserId = ownerUserId,
    ownerDeviceId = ownerDeviceId,
    name = name,
    status = status,
    visibility = visibility,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt
)

internal fun RoomSummaryResponse.toDomain(): Room = Room(
    id = id,
    ownerUserId = ownerUserId,
    ownerDeviceId = ownerDeviceId,
    name = name,
    status = status,
    visibility = visibility,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt,
    currentUserRole = currentUserRole,
    activeMemberCount = activeMemberCount
)

internal fun RoomMemberResponse.toDomain(): RoomMember = RoomMember(
    membershipId = id,
    roomId = roomId,
    userId = userId,
    username = username,
    displayName = displayName,
    avatarURL = avatarURL,
    role = role,
    membershipState = state,
    presenceState = presenceState,
    presenceLastSeenAt = presenceLastSeenAt,
    joinedAt = joinedAt,
    membershipLastSeenAt = membershipLastSeenAt,
    leftAt = leftAt
)
