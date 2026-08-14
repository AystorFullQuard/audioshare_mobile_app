package mme.corp.audioshare.room

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.model.room.RoomMember
import mme.corp.audioshare.data.model.room.toDomain
import mme.corp.audioshare.data.repository.RoomClient
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.presence.PresenceHeartbeatCoordinator

class DefaultRoomSessionCoordinator(
    private val roomClient: RoomClient,
    private val presenceCoordinator: PresenceHeartbeatCoordinator,
    private val logger: AppLogger = AppLogger.NO_OP
) : RoomSessionCoordinator {

    private val mutableState = MutableStateFlow(RoomSessionState())
    override val state: StateFlow<RoomSessionState> = mutableState.asStateFlow()

    private val stateLock = Any()
    private val operationLock = Any()
    private val generation = AtomicLong(0L)
    private val leaseSequence = AtomicLong(0L)
    private val activeLeases = mutableMapOf<Long, OperationLease>()
    private val activeOperationKeys = mutableMapOf<String, Long>()
    private var sessionUserId: String? = null
    private var sessionInitialized = false

    override suspend fun restoreFromBootstrap(
        bootstrap: SessionBootstrapResponse
    ): Result<RoomSessionState> {
        val restoreGeneration = prepareForRestore()
        val lease = beginOperation(
            operation = RoomSessionOperation.RESTORE,
            key = RESTORE_KEY,
            expectedGeneration = restoreGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            val result = restoreBootstrapSnapshot(bootstrap, lease)
            if (result.isFailure) {
                resetFailedRestoreIfCurrent(lease)
            }
            result
        } catch (exception: CancellationException) {
            resetFailedRestoreIfCurrent(lease)
            logCancellation(RoomSessionOperation.RESTORE, null)
            throw exception
        } catch (exception: Exception) {
            resetFailedRestoreIfCurrent(lease)
            logFailure(RoomSessionOperation.RESTORE, null, exception)
            Result.failure(exception)
        } finally {
            finishOperation(lease)
        }
    }

    private suspend fun restoreBootstrapSnapshot(
        bootstrap: SessionBootstrapResponse,
        lease: OperationLease
    ): Result<RoomSessionState> {
        val rooms = bootstrap.rooms.map { it.toDomain() }
        val currentRoomId = bootstrap.presence?.currentRoomId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val bootstrapUserId = bootstrap.presence?.userId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val summary = rooms.firstOrNull { it.id == currentRoomId }
        val desiredPresence = currentRoomId.toDesiredPresence()

        val committed = updateStateAndPresenceIfCurrent(
            lease = lease,
            desiredPresence = desiredPresence
        ) { current ->
            sessionInitialized = true
            sessionUserId = bootstrapUserId
            RoomSessionReducer.reduce(
                current,
                RoomSessionMutation.BootstrapSnapshot(
                    rooms = rooms,
                    currentRoom = summary
                )
            ).copy(
                isConnected = true,
                isStale = currentRoomId != null,
                lastError = null
            )
        }
        if (!committed) {
            return Result.failure(RoomSessionResetException())
        }

        logger.debug(
            TAG,
            "Desired Presence synchronized from room bootstrap; " +
                "desiredState=${desiredPresence.name}, " +
                "hasCurrentRoom=${currentRoomId != null}"
        )

        if (currentRoomId == null) {
            logger.info(
                TAG,
                "Room session restored without current room; roomCount=${rooms.size}"
            )
            return successfulState(lease)
        }

        return restoreCurrentRoom(
            roomId = currentRoomId,
            roomCount = rooms.size,
            summary = summary,
            lease = lease
        )
    }

    private suspend fun restoreCurrentRoom(
        roomId: String,
        roomCount: Int,
        summary: Room?,
        lease: OperationLease
    ): Result<RoomSessionState> {
        logger.info(
            TAG,
            "Restoring current room; roomId=$roomId, roomCount=$roomCount"
        )

        val room = roomClient.getRoom(roomId).getOrElse { exception ->
            return handleRestoreFailure(
                lease = lease,
                roomId = roomId,
                exception = exception
            )
        }
        if (room.status == RoomStatus.ARCHIVED) {
            return reconcileUnavailableRoom(
                lease = lease,
                roomId = roomId,
                operation = RoomSessionOperation.RESTORE,
                exception = ArchivedRoomSnapshotException()
            )
        }

        val members = roomClient.getActiveMembers(roomId).getOrElse { exception ->
            if (!exception.indicatesRoomUnavailable()) {
                selectRestoredRoomDetails(lease, room, summary)
            }
            return handleRestoreFailure(
                lease = lease,
                roomId = roomId,
                exception = exception
            )
        }

        val committed = updateStateIfCurrent(lease) { current ->
            val resolvedRoom = room
                .mergeMetadataFrom(summary)
                .mergeMemberSnapshot(members)
            RoomSessionReducer.reduce(
                current,
                RoomSessionMutation.RoomSelected(
                    room = resolvedRoom,
                    members = members
                )
            ).copy(
                isConnected = true,
                isStale = false,
                lastError = null
            )
        }
        if (!committed) {
            return Result.failure(RoomSessionResetException())
        }

        logger.info(
            TAG,
            "Current room restored; roomId=$roomId, memberCount=${members.size}"
        )
        return successfulState(lease)
    }

    private fun selectRestoredRoomDetails(
        lease: OperationLease,
        room: Room,
        summary: Room?
    ) {
        updateStateIfCurrent(lease) { current ->
            val existingMembers = if (current.currentRoom?.id == room.id) {
                current.activeMembers
            } else {
                emptyList()
            }
            RoomSessionReducer.reduce(
                current,
                RoomSessionMutation.RoomSelected(
                    room = room.mergeMetadataFrom(summary),
                    members = existingMembers
                )
            )
        }
    }

    private fun handleRestoreFailure(
        lease: OperationLease,
        roomId: String,
        exception: Throwable
    ): Result<RoomSessionState> {
        if (exception.indicatesRoomUnavailable()) {
            return reconcileUnavailableRoom(
                lease = lease,
                roomId = roomId,
                operation = RoomSessionOperation.RESTORE,
                exception = exception
            )
        }

        recordFailure(
            lease = lease,
            operation = RoomSessionOperation.RESTORE,
            roomId = roomId,
            exception = exception
        )
        return if (exception.isRetryable()) {
            successfulState(lease)
        } else {
            Result.failure(exception)
        }
    }

    override suspend fun loadRooms(): Result<List<Room>> {
        val expectedGeneration = requireSessionGeneration(
            RoomSessionOperation.LOAD_ROOMS
        ).getOrElse { exception ->
            return Result.failure(exception)
        }
        val lease = beginOperation(
            operation = RoomSessionOperation.LOAD_ROOMS,
            key = LOAD_ROOMS_KEY,
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            roomClient.getRooms().fold(
                onSuccess = { loadedRooms ->
                    val update = applyRoomsSnapshotIfCurrent(lease, loadedRooms)
                    if (!update.committed) {
                        Result.failure(RoomSessionResetException())
                    } else {
                        if (update.clearedCurrentRoom) {
                            logger.info(
                                TAG,
                                "Current room removed by rooms refresh; " +
                                    "desiredPresence=ONLINE"
                            )
                        }

                        logger.debug(
                            TAG,
                            "Active rooms refreshed; roomCount=${loadedRooms.size}"
                        )
                        successfulState(lease).map { it.rooms }
                    }
                },
                onFailure = { exception ->
                    recordFailure(
                        lease,
                        RoomSessionOperation.LOAD_ROOMS,
                        null,
                        exception
                    )
                    Result.failure(exception)
                }
            )
        } catch (exception: CancellationException) {
            logCancellation(RoomSessionOperation.LOAD_ROOMS, null)
            throw exception
        } finally {
            finishOperation(lease)
        }
    }

    override suspend fun openRoom(
        roomId: String
    ): Result<RoomSessionState> = activateRoomContext(
        operation = RoomSessionOperation.OPEN_ROOM,
        roomId = roomId
    )

    override suspend fun activateRoom(
        roomId: String
    ): Result<RoomSessionState> = activateRoomContext(
        operation = RoomSessionOperation.ACTIVATE,
        roomId = roomId
    )

    override suspend fun deactivateCurrentRoom(): Result<RoomSessionState> {
        val expectedGeneration = requireSessionGeneration(
            RoomSessionOperation.DEACTIVATE
        ).getOrElse { exception ->
            return Result.failure(exception)
        }
        val roomId = state.value.currentRoom?.id
            ?: return completeIdempotentDeactivation(expectedGeneration)

        return executeDeactivateRoomOperation(
            roomId = roomId,
            expectedGeneration = expectedGeneration
        )
    }

    override suspend fun refreshCurrentRoom(): Result<RoomSessionState> {
        val expectedGeneration = requireSessionGeneration(
            RoomSessionOperation.REFRESH_ROOM
        ).getOrElse { exception ->
            return Result.failure(exception)
        }
        val roomId = state.value.currentRoom?.id ?: return noCurrentRoomFailure(
            RoomSessionOperation.REFRESH_ROOM,
            expectedGeneration
        )
        val lease = beginOperation(
            operation = RoomSessionOperation.REFRESH_ROOM,
            key = "refresh-room:$roomId",
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            validateExpectedCurrentRoom(
                lease = lease,
                operation = RoomSessionOperation.REFRESH_ROOM,
                expectedRoomId = roomId
            ).getOrElse { exception ->
                return Result.failure(exception)
            }

            roomClient.getRoom(roomId).fold(
                onSuccess = { room ->
                    handleCurrentRoomRefreshSuccess(lease, roomId, room)
                },
                onFailure = { exception ->
                    handleCurrentRoomRefreshFailure(lease, roomId, exception)
                }
            )
        } catch (exception: CancellationException) {
            logCancellation(RoomSessionOperation.REFRESH_ROOM, roomId)
            throw exception
        } finally {
            finishOperation(lease)
        }
    }

    private fun handleCurrentRoomRefreshSuccess(
        lease: OperationLease,
        roomId: String,
        room: Room
    ): Result<RoomSessionState> {
        if (room.status == RoomStatus.ARCHIVED) {
            return reconcileUnavailableRoom(
                lease,
                roomId,
                RoomSessionOperation.REFRESH_ROOM,
                ArchivedRoomSnapshotException()
            )
        }

        val committed = updateStateIfCurrent(lease) { current ->
            if (current.currentRoom?.id != roomId) {
                current
            } else {
                val resolved = room.mergeMetadataFrom(current.currentRoom)
                RoomSessionReducer.reduce(
                    current,
                    RoomSessionMutation.RoomDetailsUpdated(resolved)
                ).copy(
                    isConnected = true,
                    isStale = false,
                    lastError = null
                )
            }
        }
        if (!committed) {
            return Result.failure(RoomSessionResetException())
        }

        logger.debug(TAG, "Current room details refreshed; roomId=$roomId")
        return successfulState(lease)
    }

    private fun handleCurrentRoomRefreshFailure(
        lease: OperationLease,
        roomId: String,
        exception: Throwable
    ): Result<RoomSessionState> {
        if (exception.indicatesRoomUnavailable()) {
            return reconcileUnavailableRoom(
                lease,
                roomId,
                RoomSessionOperation.REFRESH_ROOM,
                exception
            )
        }

        recordFailure(
            lease,
            RoomSessionOperation.REFRESH_ROOM,
            roomId,
            exception
        )
        return Result.failure(exception)
    }

    override suspend fun refreshActiveMembers(): Result<RoomSessionState> {
        val expectedGeneration = requireSessionGeneration(
            RoomSessionOperation.REFRESH_MEMBERS
        ).getOrElse { exception ->
            return Result.failure(exception)
        }
        val roomId = state.value.currentRoom?.id
        if (roomId == null) {
            val exception = NoCurrentRoomException()
            recordStandaloneFailureIfCurrent(
                expectedGeneration,
                RoomSessionOperation.REFRESH_MEMBERS,
                null,
                exception
            )
            return Result.failure(exception)
        }
        val lease = beginOperation(
            operation = RoomSessionOperation.REFRESH_MEMBERS,
            key = "refresh-members:$roomId",
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            validateExpectedCurrentRoom(
                lease = lease,
                operation = RoomSessionOperation.REFRESH_MEMBERS,
                expectedRoomId = roomId
            ).getOrElse { exception ->
                return Result.failure(exception)
            }

            roomClient.getActiveMembers(roomId).fold(
                onSuccess = { members ->
                    val committed = updateStateIfCurrent(lease) { current ->
                        if (current.currentRoom?.id == roomId) {
                            val withRoomMetadata = current.currentRoom
                                .mergeMemberSnapshot(members)
                            val withDetails = RoomSessionReducer.reduce(
                                current,
                                RoomSessionMutation.RoomDetailsUpdated(withRoomMetadata)
                            )
                            RoomSessionReducer.reduce(
                                withDetails,
                                RoomSessionMutation.MembersUpdated(
                                    roomId = roomId,
                                    members = members
                                )
                            ).copy(
                                isConnected = true,
                                isStale = false,
                                lastError = null
                            )
                        } else {
                            current
                        }
                    }
                    if (!committed) {
                        Result.failure(RoomSessionResetException())
                    } else {
                        logger.debug(
                            TAG,
                            "Active room members refreshed; roomId=$roomId, " +
                                "memberCount=${members.size}"
                        )
                        successfulState(lease)
                    }
                },
                onFailure = { exception ->
                    if (exception.indicatesRoomUnavailable()) {
                        reconcileUnavailableRoom(
                            lease,
                            roomId,
                            RoomSessionOperation.REFRESH_MEMBERS,
                            exception
                        )
                    } else {
                        recordFailure(
                            lease,
                            RoomSessionOperation.REFRESH_MEMBERS,
                            roomId,
                            exception
                        )
                        Result.failure(exception)
                    }
                }
            )
        } catch (exception: CancellationException) {
            logCancellation(RoomSessionOperation.REFRESH_MEMBERS, roomId)
            throw exception
        } finally {
            finishOperation(lease)
        }
    }

    override suspend fun createRoom(
        name: String?,
        visibility: RoomVisibility
    ): Result<RoomSessionState> {
        val expectedGeneration = requireSessionGeneration(
            RoomSessionOperation.CREATE
        ).getOrElse { exception ->
            return Result.failure(exception)
        }
        return executeMembershipOperation(
            operation = RoomSessionOperation.CREATE,
            roomId = null,
            expectedGeneration = expectedGeneration
        ) {
            roomClient.createRoom(name, visibility)
        }
    }

    override suspend fun joinLocalDiscoveryRoom(
        roomId: String
    ): Result<RoomSessionState> {
        val expectedGeneration = requireSessionGeneration(
            RoomSessionOperation.JOIN
        ).getOrElse { exception ->
            return Result.failure(exception)
        }
        val resolvedRoomId = validateRoomId(roomId).getOrElse { exception ->
            recordStandaloneFailureIfCurrent(
                expectedGeneration,
                RoomSessionOperation.JOIN,
                roomId,
                exception
            )
            return Result.failure(exception)
        }

        return executeMembershipOperation(
            operation = RoomSessionOperation.JOIN,
            roomId = resolvedRoomId,
            expectedGeneration = expectedGeneration
        ) {
            roomClient.joinLocalDiscoveryRoom(resolvedRoomId)
        }
    }

    override suspend fun leaveCurrentRoom(): Result<RoomSessionState> {
        val expectedGeneration = requireSessionGeneration(
            RoomSessionOperation.LEAVE
        ).getOrElse { exception ->
            return Result.failure(exception)
        }
        val roomId = state.value.currentRoom?.id
            ?: return completeIdempotentExit(
                RoomSessionOperation.LEAVE,
                expectedGeneration
            )

        return executeExitRoomOperation(
            operation = RoomSessionOperation.LEAVE,
            roomId = roomId,
            expectedGeneration = expectedGeneration
        ) {
            roomClient.leaveRoom(roomId)
        }
    }

    override suspend fun archiveCurrentRoom(): Result<RoomSessionState> {
        val expectedGeneration = requireSessionGeneration(
            RoomSessionOperation.ARCHIVE
        ).getOrElse { exception ->
            return Result.failure(exception)
        }
        val roomId = state.value.currentRoom?.id
            ?: return completeIdempotentExit(
                RoomSessionOperation.ARCHIVE,
                expectedGeneration
            )

        return executeExitRoomOperation(
            operation = RoomSessionOperation.ARCHIVE,
            roomId = roomId,
            expectedGeneration = expectedGeneration
        ) {
            roomClient.archiveRoom(roomId)
        }
    }

    override fun markDisconnected() {
        val updated = synchronized(operationLock) {
            synchronized(stateLock) {
                if (!sessionInitialized) {
                    false
                } else {
                    val current = mutableState.value
                    mutableState.value = current.copy(
                        isConnected = false,
                        isStale = current.rooms.isNotEmpty() ||
                            current.currentRoom != null
                    )
                    true
                }
            }
        }
        if (updated) {
            logger.info(
                TAG,
                "Room runtime marked disconnected; confirmed state retained"
            )
        }
    }

    override suspend fun reconnect(): Result<RoomSessionState> {
        val expectedGeneration = requireSessionGeneration(
            RoomSessionOperation.RECONNECT
        ).getOrElse { exception ->
            return Result.failure(exception)
        }
        val lease = beginOperation(
            operation = RoomSessionOperation.RECONNECT,
            key = LIFECYCLE_KEY,
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            val previous = state.value
            val rooms = roomClient.getRooms().getOrElse { exception ->
                recordFailure(
                    lease,
                    RoomSessionOperation.RECONNECT,
                    previous.currentRoom?.id,
                    exception
                )
                return Result.failure(exception)
            }.map { room ->
                room.mergeMetadataFrom(
                    previous.rooms.firstOrNull { it.id == room.id }
                )
            }

            val currentId = previous.currentRoom?.id
            if (currentId == null || rooms.none { it.id == currentId }) {
                val transform: (RoomSessionState) -> RoomSessionState = { current ->
                    RoomSessionReducer.reduce(
                        current,
                        RoomSessionMutation.RoomsReplaced(rooms)
                    ).copy(
                        isConnected = true,
                        isStale = false,
                        lastError = null
                    )
                }
                val committed = if (currentId != null) {
                    updateStateAndPresenceIfCurrent(
                        lease,
                        PresenceState.ONLINE,
                        transform
                    )
                } else {
                    updateStateIfCurrent(lease, transform)
                }
                if (!committed) {
                    return Result.failure(RoomSessionResetException())
                }
                logger.info(TAG, "Room reconnect completed without current room")
                return successfulState(lease)
            }

            val roomsCommitted = updateStateIfCurrent(lease) { current ->
                RoomSessionReducer.reduce(
                    current,
                    RoomSessionMutation.RoomsReplaced(
                        rooms = rooms,
                        clearSelectionIfMissing = false
                    )
                )
            }
            if (!roomsCommitted) {
                return Result.failure(RoomSessionResetException())
            }

            val room = roomClient.getRoom(currentId).getOrElse { exception ->
                if (exception.indicatesRoomUnavailable()) {
                    return reconcileUnavailableRoom(
                        lease,
                        currentId,
                        RoomSessionOperation.RECONNECT,
                        exception
                    )
                }
                recordFailure(
                    lease,
                    RoomSessionOperation.RECONNECT,
                    currentId,
                    exception
                )
                return Result.failure(exception)
            }
            if (room.status == RoomStatus.ARCHIVED) {
                return reconcileUnavailableRoom(
                    lease,
                    currentId,
                    RoomSessionOperation.RECONNECT,
                    ArchivedRoomSnapshotException()
                )
            }
            val members = roomClient.getActiveMembers(currentId)
                .getOrElse { exception ->
                    if (exception.indicatesRoomUnavailable()) {
                        return reconcileUnavailableRoom(
                            lease,
                            currentId,
                            RoomSessionOperation.RECONNECT,
                            exception
                        )
                    }
                    recordFailure(
                        lease,
                        RoomSessionOperation.RECONNECT,
                        currentId,
                        exception
                    )
                    return Result.failure(exception)
                }

            val committed = updateStateIfCurrent(lease) { current ->
                val resolved = room
                    .mergeMetadataFrom(
                        rooms.firstOrNull { it.id == currentId }
                    )
                    .mergeMemberSnapshot(members)
                RoomSessionReducer.reduce(
                    current,
                    RoomSessionMutation.RoomSelected(
                        room = resolved,
                        members = members
                    )
                ).copy(
                    isConnected = true,
                    isStale = false,
                    lastError = null
                )
            }
            if (!committed) {
                return Result.failure(RoomSessionResetException())
            }
            logger.info(
                TAG,
                "Room reconnect completed; roomId=$currentId, memberCount=${members.size}"
            )
            successfulState(lease)
        } catch (exception: CancellationException) {
            logCancellation(RoomSessionOperation.RECONNECT, state.value.currentRoom?.id)
            throw exception
        } finally {
            finishOperation(lease)
        }
    }

    override fun clearForLogout() {
        resetSession(reason = "logout")
    }

    override fun resetAfterStartupFailure() {
        resetSession(reason = "startup_failure")
    }

    private fun resetSession(reason: String) {
        synchronized(operationLock) {
            synchronized(stateLock) {
                resetSessionLocked()
            }
        }
        logger.info(TAG, "Room session cleared; reason=$reason")
    }

    private fun resetFailedRestoreIfCurrent(
        lease: OperationLease
    ): Boolean {
        val reset = synchronized(operationLock) {
            synchronized(stateLock) {
                if (!lease.isCurrent()) {
                    false
                } else {
                    resetSessionLocked()
                    true
                }
            }
        }
        if (reset) {
            logger.info(TAG, "Room session cleared; reason=restore_failure")
        }
        return reset
    }

    private fun resetSessionLocked() {
        generation.incrementAndGet()
        activeLeases.clear()
        activeOperationKeys.clear()
        sessionInitialized = false
        sessionUserId = null
        mutableState.value = RoomSessionReducer.reduce(
            mutableState.value,
            RoomSessionMutation.Cleared
        )
        presenceCoordinator.setDesiredState(null)
    }

    private suspend fun activateRoomContext(
        operation: RoomSessionOperation,
        roomId: String
    ): Result<RoomSessionState> {
        val expectedGeneration = requireSessionGeneration(operation)
            .getOrElse { exception ->
                return Result.failure(exception)
            }
        val resolvedRoomId = validateRoomId(roomId).getOrElse { exception ->
            recordStandaloneFailureIfCurrent(
                expectedGeneration,
                operation,
                roomId,
                exception
            )
            return Result.failure(exception)
        }

        return executeActiveRoomOperation(
            operation = operation,
            roomId = resolvedRoomId,
            expectedGeneration = expectedGeneration
        ) {
            roomClient.activateRoom(resolvedRoomId)
        }
    }

    private fun completeIdempotentDeactivation(
        expectedGeneration: Long
    ): Result<RoomSessionState> {
        val lease = beginOperation(
            operation = RoomSessionOperation.DEACTIVATE,
            key = LIFECYCLE_KEY,
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            validateExpectedCurrentRoom(
                lease = lease,
                operation = RoomSessionOperation.DEACTIVATE,
                expectedRoomId = null
            ).getOrElse { exception ->
                return Result.failure(exception)
            }

            val committed = updateStateAndPresenceIfCurrent(
                lease = lease,
                desiredPresence = PresenceState.ONLINE
            ) { current ->
                current.copy(
                    activeMembers = emptyList(),
                    lastError = null
                )
            }
            if (!committed) {
                Result.failure(RoomSessionResetException())
            } else {
                logger.debug(
                    TAG,
                    "Room deactivate treated as idempotent: no current room"
                )
                successfulState(lease)
            }
        } finally {
            finishOperation(lease)
        }
    }

    private fun completeIdempotentExit(
        operation: RoomSessionOperation,
        expectedGeneration: Long
    ): Result<RoomSessionState> {
        val lease = beginOperation(
            operation = operation,
            key = LIFECYCLE_KEY,
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            validateExpectedCurrentRoom(
                lease = lease,
                operation = operation,
                expectedRoomId = null
            ).getOrElse { exception ->
                return Result.failure(exception)
            }

            val committed = updateStateAndPresenceIfCurrent(
                lease = lease,
                desiredPresence = PresenceState.ONLINE
            ) { current ->
                current.copy(activeMembers = emptyList(), lastError = null)
            }
            if (!committed) {
                Result.failure(RoomSessionResetException())
            } else {
                logger.debug(
                    TAG,
                    "Room ${operation.name.lowercase()} treated as idempotent: " +
                        "no current room"
                )
                successfulState(lease)
            }
        } finally {
            finishOperation(lease)
        }
    }

    private suspend fun executeActiveRoomOperation(
        operation: RoomSessionOperation,
        roomId: String?,
        expectedGeneration: Long,
        request: suspend () -> Result<Room>
    ): Result<RoomSessionState> {
        val lease = beginOperation(
            operation = operation,
            key = LIFECYCLE_KEY,
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            val room = request().getOrElse { exception ->
                recordFailure(lease, operation, roomId, exception)
                return Result.failure(exception)
            }

            val committed = updateStateAndPresenceIfCurrent(
                lease = lease,
                desiredPresence = PresenceState.IN_ROOM
            ) { current ->
                val resolved = room
                    .mergeMetadataFrom(
                        current.rooms.firstOrNull { it.id == room.id }
                    )
                    .withEnterDefaults(operation)
                RoomSessionReducer.reduce(
                    current,
                    roomEnterMutation(
                        operation = operation,
                        room = resolved
                    )
                ).copy(
                    isConnected = true,
                    isStale = false,
                    lastError = null
                )
            }
            if (!committed) {
                return Result.failure(RoomSessionResetException())
            }

            logger.debug(
                TAG,
                "Desired Presence updated after room enter; " +
                    "roomId=${room.id}, desiredState=IN_ROOM"
            )

            refreshMembersAfterEnter(room.id, lease).getOrElse { exception ->
                return Result.failure(exception)
            }

            logger.info(
                TAG,
                "Room ${operation.name.lowercase()} committed; " +
                    "roomId=${room.id}, desiredPresence=IN_ROOM"
            )
            successfulState(lease)
        } catch (exception: CancellationException) {
            logCancellation(operation, roomId)
            throw exception
        } finally {
            finishOperation(lease)
        }
    }

    private suspend fun executeMembershipOperation(
        operation: RoomSessionOperation,
        roomId: String?,
        expectedGeneration: Long,
        request: suspend () -> Result<Room>
    ): Result<RoomSessionState> {
        val lease = beginOperation(
            operation = operation,
            key = LIFECYCLE_KEY,
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            val room = request().getOrElse { exception ->
                recordFailure(lease, operation, roomId, exception)
                return Result.failure(exception)
            }

            val committed = updateStateIfCurrent(lease) { current ->
                val resolved = room
                    .mergeMetadataFrom(
                        current.rooms.firstOrNull { it.id == room.id }
                    )
                    .withEnterDefaults(operation)
                RoomSessionReducer.reduce(
                    current,
                    RoomSessionMutation.RoomDetailsUpdated(resolved)
                ).copy(
                    isConnected = true,
                    lastError = null
                )
            }
            if (!committed) {
                return Result.failure(RoomSessionResetException())
            }

            logger.info(
                TAG,
                "Room ${operation.name.lowercase()} membership committed; " +
                    "roomId=${room.id}, activeRoomChanged=false"
            )
            successfulState(lease)
        } catch (exception: CancellationException) {
            logCancellation(operation, roomId)
            throw exception
        } finally {
            finishOperation(lease)
        }
    }

    private suspend fun executeDeactivateRoomOperation(
        roomId: String,
        expectedGeneration: Long
    ): Result<RoomSessionState> {
        val lease = beginOperation(
            operation = RoomSessionOperation.DEACTIVATE,
            key = LIFECYCLE_KEY,
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            validateExpectedCurrentRoom(
                lease = lease,
                operation = RoomSessionOperation.DEACTIVATE,
                expectedRoomId = roomId
            ).getOrElse { exception ->
                return Result.failure(exception)
            }

            roomClient.deactivateRoom(roomId).getOrElse { exception ->
                recordFailure(
                    lease,
                    RoomSessionOperation.DEACTIVATE,
                    roomId,
                    exception
                )
                return Result.failure(exception)
            }

            val committed = commitDeactivationIfCurrent(lease, roomId)
            if (!committed) {
                return Result.failure(RoomSessionResetException())
            }

            logger.info(
                TAG,
                "Room deactivate committed; roomId=$roomId, " +
                    "desiredPresence=ONLINE"
            )
            successfulState(lease)
        } catch (exception: CancellationException) {
            logCancellation(RoomSessionOperation.DEACTIVATE, roomId)
            throw exception
        } finally {
            finishOperation(lease)
        }
    }

    private suspend fun executeExitRoomOperation(
        operation: RoomSessionOperation,
        roomId: String,
        expectedGeneration: Long,
        request: suspend () -> Result<Room>
    ): Result<RoomSessionState> {
        val lease = beginOperation(
            operation = operation,
            key = LIFECYCLE_KEY,
            expectedGeneration = expectedGeneration
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return try {
            validateExpectedCurrentRoom(
                lease = lease,
                operation = operation,
                expectedRoomId = roomId
            ).getOrElse { exception ->
                return Result.failure(exception)
            }

            request().getOrElse { exception ->
                recordFailure(lease, operation, roomId, exception)
                return Result.failure(exception)
            }

            val committed = updateStateAndPresenceIfCurrent(
                lease = lease,
                desiredPresence = PresenceState.ONLINE
            ) { current ->
                RoomSessionReducer.reduce(
                    current,
                    RoomSessionMutation.RoomRemoved(roomId)
                ).copy(
                    isConnected = true,
                    isStale = false,
                    lastError = null
                )
            }
            if (!committed) {
                return Result.failure(RoomSessionResetException())
            }

            logger.info(
                TAG,
                "Room ${operation.name.lowercase()} committed; " +
                    "roomId=$roomId, desiredPresence=ONLINE"
            )
            successfulState(lease)
        } catch (exception: CancellationException) {
            logCancellation(operation, roomId)
            throw exception
        } finally {
            finishOperation(lease)
        }
    }

    private suspend fun refreshMembersAfterEnter(
        roomId: String,
        lease: OperationLease
    ): Result<Unit> = roomClient.getActiveMembers(roomId).fold(
        onSuccess = { members ->
            val committed = updateStateIfCurrent(lease) { current ->
                val selectedRoom = current.currentRoom
                if (selectedRoom?.id != roomId) {
                    current
                } else {
                    val withDetails = RoomSessionReducer.reduce(
                        current,
                        RoomSessionMutation.RoomDetailsUpdated(
                            selectedRoom.mergeMemberSnapshot(members)
                        )
                    )
                    RoomSessionReducer.reduce(
                        withDetails,
                        RoomSessionMutation.MembersUpdated(
                            roomId = roomId,
                            members = members
                        )
                    )
                }
            }
            if (!committed) {
                Result.failure(RoomSessionResetException())
            } else {
                logger.debug(
                    TAG,
                    "Active members loaded after room enter; " +
                        "roomId=$roomId, memberCount=${members.size}"
                )
                Result.success(Unit)
            }
        },
        onFailure = { exception ->
            if (exception.indicatesRoomUnavailable()) {
                val committed = clearCurrentRoomAfterServerRejection(
                    lease,
                    roomId,
                    RoomSessionOperation.REFRESH_MEMBERS,
                    exception
                )
                if (committed) {
                    Result.failure(exception)
                } else {
                    Result.failure(RoomSessionResetException())
                }
            } else {
                // Room transition already succeeded; keep it and expose stale members.
                recordFailure(
                    lease,
                    RoomSessionOperation.REFRESH_MEMBERS,
                    roomId,
                    exception
                )
                Result.success(Unit)
            }
        }
    )

    private fun commitDeactivationIfCurrent(
        lease: OperationLease,
        roomId: String
    ): Boolean = synchronized(operationLock) {
        synchronized(stateLock) {
            if (!lease.isCurrent()) {
                logger.debug(
                    TAG,
                    "Room deactivation ignored for stale operation; " +
                        "leaseId=${lease.id}, roomId=$roomId"
                )
                false
            } else if (mutableState.value.currentRoom?.id != roomId) {
                logger.debug(
                    TAG,
                    "Room deactivation ignored after active room changed; " +
                        "leaseId=${lease.id}, roomId=$roomId"
                )
                false
            } else {
                mutableState.value = RoomSessionReducer.reduce(
                    mutableState.value,
                    RoomSessionMutation.RoomDeactivated(roomId)
                ).copy(
                    isConnected = true,
                    isStale = false,
                    lastError = null
                )
                presenceCoordinator.setDesiredState(PresenceState.ONLINE)
                true
            }
        }
    }

    private fun roomEnterMutation(
        operation: RoomSessionOperation,
        room: Room
    ): RoomSessionMutation = if (operation == RoomSessionOperation.ACTIVATE) {
        RoomSessionMutation.RoomActivated(
            room = room,
            members = emptyList()
        )
    } else {
        RoomSessionMutation.RoomSelected(
            room = room,
            members = emptyList()
        )
    }

    private fun applyRoomsSnapshotIfCurrent(
        lease: OperationLease,
        loadedRooms: List<Room>
    ): RoomsSnapshotUpdate = synchronized(operationLock) operationLock@{
        synchronized(stateLock) stateLock@{
            if (!lease.isCurrent()) {
                return@stateLock RoomsSnapshotUpdate(committed = false)
            }

            val current = mutableState.value
            val mergedRooms = loadedRooms.map { room ->
                room.mergeMetadataFrom(
                    current.rooms.firstOrNull { it.id == room.id }
                )
            }
            val selectedRoom = current.currentRoom
                ?.takeIf { selected ->
                    mergedRooms.any { it.id == selected.id }
                }
                ?.let { selected ->
                    mergedRooms.first { it.id == selected.id }
                        .mergeMetadataFrom(selected)
                }
            val reduced = RoomSessionReducer.reduce(
                current,
                RoomSessionMutation.RoomsReplaced(mergedRooms)
            )
            val clearedCurrentRoom = current.currentRoom != null &&
                reduced.currentRoom == null

            mutableState.value = reduced.copy(
                currentRoom = selectedRoom,
                isConnected = true,
                isStale = false,
                lastError = null
            )
            if (clearedCurrentRoom) {
                presenceCoordinator.setDesiredState(PresenceState.ONLINE)
            }

            RoomsSnapshotUpdate(
                committed = true,
                clearedCurrentRoom = clearedCurrentRoom
            )
        }
    }

    private fun clearCurrentRoomAfterServerRejection(
        lease: OperationLease,
        roomId: String,
        operation: RoomSessionOperation,
        exception: Throwable
    ): Boolean {
        var clearedCurrentRoom = false
        val committed = synchronized(operationLock) {
            synchronized(stateLock) {
                if (!lease.isCurrent()) {
                    false
                } else {
                    val current = mutableState.value
                    clearedCurrentRoom = current.currentRoom?.id == roomId
                    mutableState.value = RoomSessionReducer.reduce(
                        current,
                        RoomSessionMutation.RoomRemoved(roomId)
                    ).copy(
                        isConnected = true,
                        isStale = false,
                        lastError = exception.toSessionError(operation, roomId)
                    )
                    if (clearedCurrentRoom) {
                        presenceCoordinator.setDesiredState(PresenceState.ONLINE)
                    }
                    true
                }
            }
        }

        if (!committed) {
            logger.debug(
                TAG,
                "Room rejection ignored for stale operation; " +
                    "operation=${operation.name}, roomId=$roomId"
            )
            return false
        }

        logger.warn(
            TAG,
            "Server rejected room state; roomId=$roomId, " +
                "clearedCurrentRoom=$clearedCurrentRoom, " +
                    exception.safeSummary()
        )
        return true
    }

    private fun validateExpectedCurrentRoom(
        lease: OperationLease,
        operation: RoomSessionOperation,
        expectedRoomId: String?
    ): Result<Unit> {
        var selectionError: RoomSelectionChangedException? = null
        val result = synchronized(operationLock) {
            synchronized(stateLock) {
                if (!lease.isCurrent()) {
                    Result.failure(RoomSessionResetException())
                } else {
                    val actualRoomId = mutableState.value.currentRoom?.id
                    if (actualRoomId == expectedRoomId) {
                        Result.success(Unit)
                    } else {
                        val exception = RoomSelectionChangedException()
                        selectionError = exception
                        mutableState.value = mutableState.value.copy(
                            lastError = exception.toSessionError(
                                operation,
                                expectedRoomId
                            )
                        )
                        Result.failure(exception)
                    }
                }
            }
        }
        selectionError?.let { exception ->
            logFailure(operation, expectedRoomId, exception)
        }
        return result
    }

    private fun reconcileUnavailableRoom(
        lease: OperationLease,
        roomId: String,
        operation: RoomSessionOperation,
        exception: Throwable
    ): Result<RoomSessionState> {
        val committed = clearCurrentRoomAfterServerRejection(
            lease,
            roomId,
            operation,
            exception
        )
        return if (committed) {
            successfulState(lease)
        } else {
            Result.failure(RoomSessionResetException())
        }
    }

    private fun requireSessionGeneration(
        operation: RoomSessionOperation
    ): Result<Long> = synchronized(operationLock) {
        if (!sessionInitialized) {
            val exception = RoomSessionNotInitializedException()
            logger.debug(
                TAG,
                "Room session operation rejected before bootstrap or after logout; " +
                    "operation=${operation.name}"
            )
            Result.failure(exception)
        } else {
            Result.success(generation.get())
        }
    }

    private fun successfulState(
        lease: OperationLease
    ): Result<RoomSessionState> = synchronized(operationLock) {
        synchronized(stateLock) {
            if (!lease.isCurrent()) {
                Result.failure(RoomSessionResetException())
            } else {
                val remainingOperations = activeLeases.values
                    .asSequence()
                    .filterNot { it.id == lease.id }
                    .mapTo(linkedSetOf()) { it.operation }
                Result.success(
                    mutableState.value.copy(
                        activeOperations = remainingOperations
                    )
                )
            }
        }
    }

    private fun beginOperation(
        operation: RoomSessionOperation,
        key: String,
        expectedGeneration: Long = generation.get()
    ): Result<OperationLease> {
        synchronized(operationLock) {
            if (operation != RoomSessionOperation.RESTORE && !sessionInitialized) {
                val exception = RoomSessionNotInitializedException()
                logger.debug(
                    TAG,
                    "Room session operation rejected before bootstrap or after logout; " +
                        "operation=${operation.name}"
                )
                return Result.failure(exception)
            }
            if (expectedGeneration != generation.get()) {
                logger.debug(
                    TAG,
                    "Room session operation rejected after session reset; " +
                        "operation=${operation.name}"
                )
                return Result.failure(RoomSessionResetException())
            }
            // RoomSessionState is a single ordered state machine. Serializing
            // operations prevents stale list/detail/member responses from
            // overwriting a newer lifecycle transition.
            if (activeLeases.isNotEmpty()) {
                val exception = RoomOperationInProgressException(operation)
                recordStandaloneFailure(operation, exception)
                return Result.failure(exception)
            }

            val lease = OperationLease(
                id = leaseSequence.incrementAndGet(),
                operation = operation,
                key = key,
                generation = expectedGeneration
            )
            activeLeases[lease.id] = lease
            activeOperationKeys[key] = lease.id
            publishActiveOperationsLocked()
            logger.debug(
                TAG,
                "Room session operation started; operation=${operation.name}, " +
                    "leaseId=${lease.id}"
            )

            return Result.success(lease)
        }
    }

    private fun finishOperation(lease: OperationLease) {
        synchronized(operationLock) {
            val removed = activeLeases.remove(lease.id) ?: return
            if (activeOperationKeys[removed.key] == removed.id) {
                activeOperationKeys.remove(removed.key)
            }
            publishActiveOperationsLocked()
        }
    }

    private fun prepareForRestore(): Long = synchronized(operationLock) {
        synchronized(stateLock) {
            val nextGeneration = generation.incrementAndGet()
            activeLeases.clear()
            activeOperationKeys.clear()
            sessionInitialized = false
            sessionUserId = null
            mutableState.value = mutableState.value.copy(
                activeOperations = emptySet()
            )
            nextGeneration
        }
    }

    private fun publishActiveOperationsLocked() {
        val operations = activeLeases.values
            .mapTo(linkedSetOf()) { it.operation }
        updateState { current -> current.copy(activeOperations = operations) }
    }

    private fun recordFailure(
        lease: OperationLease,
        operation: RoomSessionOperation,
        roomId: String?,
        exception: Throwable
    ) {
        val retryable = exception.isRetryable()
        val committed = updateStateIfCurrent(lease) { current ->
            current.copy(
                isConnected = current.isConnected && !retryable,
                isStale = if (retryable) {
                    current.rooms.isNotEmpty() || current.currentRoom != null
                } else {
                    current.isStale
                },
                lastError = exception.toSessionError(operation, roomId)
            )
        }
        if (committed) {
            logFailure(operation, roomId, exception)
        }
    }

    private fun recordStandaloneFailureIfCurrent(
        expectedGeneration: Long,
        operation: RoomSessionOperation,
        roomId: String?,
        exception: Throwable
    ) {
        val committed = synchronized(operationLock) {
            synchronized(stateLock) {
                if (!sessionInitialized || expectedGeneration != generation.get()) {
                    false
                } else {
                    mutableState.value = mutableState.value.copy(
                        lastError = exception.toSessionError(operation, roomId)
                    )
                    true
                }
            }
        }
        if (committed) {
            logFailure(operation, roomId, exception)
        }
    }

    private fun recordStandaloneFailure(
        operation: RoomSessionOperation,
        exception: Throwable
    ) {
        updateState { current ->
            current.copy(lastError = exception.toSessionError(operation, null))
        }
        logFailure(operation, null, exception)
    }

    private fun logCancellation(
        operation: RoomSessionOperation,
        roomId: String?
    ) {
        val context = roomId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { ", roomId=$it" }
            .orEmpty()
        logger.debug(
            TAG,
            "Room session operation cancelled; " +
                "operation=${operation.name}$context"
        )
    }

    private fun logFailure(
        operation: RoomSessionOperation,
        roomId: String?,
        exception: Throwable
    ) {
        val message = buildString {
            append("Room session ")
            append(operation.name.lowercase())
            append(" failed: ")
            if (!roomId.isNullOrBlank()) {
                append("roomId=")
                append(roomId.trim())
                append(", ")
            }
            append(exception.safeSummary())
        }

        when {
            exception is RoomOperationInProgressException ||
                exception is RoomSessionNotInitializedException ||
                exception is RoomSelectionChangedException ||
                exception is NoCurrentRoomException ||
                (exception is ApiException && exception.httpCode < 500) ->
                logger.warn(TAG, message)

            exception is ApiException -> logger.error(TAG, message)
            else -> logger.error(TAG, message, exception)
        }
    }

    private fun Throwable.toSessionError(
        operation: RoomSessionOperation,
        roomId: String?
    ): RoomSessionError = if (this is ApiException) {
        RoomSessionError(
            operation = operation,
            roomId = roomId,
            type = "ApiException",
            httpCode = httpCode,
            apiCode = apiError.code,
            retryable = isRetryable()
        )
    } else {
        RoomSessionError(
            operation = operation,
            roomId = roomId,
            type = safeTypeName(),
            retryable = isRetryable()
        )
    }

    private fun Throwable.safeSummary(): String = if (this is ApiException) {
        "type=ApiException, httpCode=$httpCode, apiCode=${apiError.code}"
    } else {
        "type=${safeTypeName()}"
    }

    private fun Throwable.safeTypeName(): String =
        this::class.java.simpleName.ifBlank { "Throwable" }

    private fun Throwable.isRetryable(): Boolean =
        this is IOException ||
            (this is ApiException && httpCode in RETRYABLE_HTTP_CODES)

    private fun Throwable.indicatesRoomUnavailable(): Boolean =
        this is ArchivedRoomSnapshotException ||
            (this is ApiException && apiError.code in ROOM_GONE_CODES)

    private fun noCurrentRoomFailure(
        operation: RoomSessionOperation,
        expectedGeneration: Long
    ): Result<RoomSessionState> {
        val exception = NoCurrentRoomException()
        recordStandaloneFailureIfCurrent(
            expectedGeneration,
            operation,
            null,
            exception
        )
        return Result.failure(exception)
    }

    private fun String?.toDesiredPresence(): PresenceState =
        if (this == null) PresenceState.ONLINE else PresenceState.IN_ROOM

    private fun validateRoomId(roomId: String): Result<String> {
        val resolved = roomId.trim()
        return if (resolved.isEmpty()) {
            Result.failure(IllegalArgumentException("roomId is required"))
        } else {
            Result.success(resolved)
        }
    }

    private fun Room.mergeMetadataFrom(other: Room?): Room = copy(
        currentUserRole = currentUserRole ?: other?.currentUserRole,
        activeMemberCount = activeMemberCount ?: other?.activeMemberCount
    )

    private fun Room.mergeMemberSnapshot(members: List<RoomMember>): Room {
        val currentRole = sessionUserId?.let { userId ->
            members.firstOrNull { it.userId == userId }?.role
        }
        return copy(
            currentUserRole = currentRole ?: currentUserRole,
            activeMemberCount = members.size.toLong()
        )
    }

    private fun Room.withEnterDefaults(
        operation: RoomSessionOperation
    ): Room {
        val fallbackRole = when {
            operation == RoomSessionOperation.CREATE -> RoomMemberRole.OWNER
            sessionUserId != null && ownerUserId == sessionUserId ->
                RoomMemberRole.OWNER
            operation == RoomSessionOperation.JOIN ||
                operation == RoomSessionOperation.OPEN_ROOM ->
                RoomMemberRole.MEMBER
            else -> null
        }
        return copy(
            currentUserRole = currentUserRole ?: fallbackRole,
            activeMemberCount = activeMemberCount ?: 1L
        )
    }

    private fun updateState(transform: (RoomSessionState) -> RoomSessionState) {
        synchronized(stateLock) {
            mutableState.value = transform(mutableState.value)
        }
    }

    private fun updateStateIfCurrent(
        lease: OperationLease,
        transform: (RoomSessionState) -> RoomSessionState
    ): Boolean = synchronized(operationLock) {
        synchronized(stateLock) {
            if (!lease.isCurrent()) {
                logger.debug(
                    TAG,
                    "Room state update ignored for stale operation; " +
                        "operation=${lease.operation.name}, leaseId=${lease.id}"
                )
                false
            } else {
                mutableState.value = transform(mutableState.value)
                true
            }
        }
    }

    private fun updateStateAndPresenceIfCurrent(
        lease: OperationLease,
        desiredPresence: PresenceState?,
        transform: (RoomSessionState) -> RoomSessionState
    ): Boolean = synchronized(operationLock) {
        synchronized(stateLock) {
            if (!lease.isCurrent()) {
                logger.debug(
                    TAG,
                    "Room state and Presence update ignored for stale operation; " +
                        "operation=${lease.operation.name}, leaseId=${lease.id}"
                )
                false
            } else {
                mutableState.value = transform(mutableState.value)
                presenceCoordinator.setDesiredState(desiredPresence)
                true
            }
        }
    }

    private fun OperationLease.isCurrent(): Boolean =
        generation == this@DefaultRoomSessionCoordinator.generation.get() &&
            activeLeases[id] == this

    private data class RoomsSnapshotUpdate(
        val committed: Boolean,
        val clearedCurrentRoom: Boolean = false
    )

    private data class OperationLease(
        val id: Long,
        val operation: RoomSessionOperation,
        val key: String,
        val generation: Long
    )

    private class RoomOperationInProgressException(
        operation: RoomSessionOperation
    ) : IllegalStateException("Room operation is already in progress: $operation")

    private class NoCurrentRoomException :
        IllegalStateException("Current room is not selected")

    private class RoomSessionResetException :
        IllegalStateException("Room session was reset")

    private class RoomSessionNotInitializedException :
        IllegalStateException("Room session is not initialized")

    private class RoomSelectionChangedException :
        IllegalStateException("Current room changed before operation started")

    private class ArchivedRoomSnapshotException :
        IllegalStateException("Room snapshot is archived")

    private companion object {
        const val TAG = "RoomSession"
        const val RESTORE_KEY = "restore"
        const val LOAD_ROOMS_KEY = "load-rooms"
        const val LIFECYCLE_KEY = "lifecycle"

        val RETRYABLE_HTTP_CODES = setOf(502, 503, 504)
        val ROOM_GONE_CODES = setOf(
            "ROOM_ARCHIVED",
            "ROOM_NOT_FOUND",
            "ROOM_NOT_VISIBLE"
        )
    }
}
