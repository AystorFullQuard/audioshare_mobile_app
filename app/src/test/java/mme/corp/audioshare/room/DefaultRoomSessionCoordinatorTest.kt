package mme.corp.audioshare.room

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.dto.bootstrap.RoomSummaryResponse
import mme.corp.audioshare.data.dto.bootstrap.SessionBootstrapResponse
import mme.corp.audioshare.data.dto.bootstrap.SessionPresenceResponse
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomMemberState
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.model.room.RoomMember
import mme.corp.audioshare.data.repository.RoomClient
import mme.corp.audioshare.exception.ApiErrorResponse
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.presence.PresenceHeartbeatCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRoomSessionCoordinatorTest {

    @Test
    fun restoreUsesBootstrapCurrentRoomAndPreservesSummaryMetadata() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)

        val result = coordinator.restoreFromBootstrap(
            sessionBootstrap(currentRoomId = "room-1")
        )

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().isBusy)
        assertTrue(result.getOrThrow().activeOperations.isEmpty())
        assertEquals("room-1", coordinator.state.value.currentRoom?.id)
        assertEquals(
            RoomMemberRole.MEMBER,
            coordinator.state.value.currentRoom?.currentUserRole
        )
        assertEquals(1L, coordinator.state.value.currentRoom?.activeMemberCount)
        assertEquals(1, coordinator.state.value.activeMembers.size)
        assertEquals(listOf(PresenceState.IN_ROOM), presence.desiredStates)
    }


    @Test
    fun restoreSupportsUnnamedRoomSnapshots() = runTest {
        val roomClient = FakeRoomClient().apply {
            getRoomHandler = { Result.success(room(it, name = null)) }
        }
        val coordinator = DefaultRoomSessionCoordinator(
            roomClient,
            FakePresenceCoordinator()
        )

        val result = coordinator.restoreFromBootstrap(
            sessionBootstrap(currentRoomId = "room-1", roomName = null)
        )

        assertTrue(result.isSuccess)
        assertNull(coordinator.state.value.currentRoom?.name)
    }

    @Test
    fun retryableRestoreFailureKeepsBootstrapSnapshotForReconnect() = runTest {
        val roomClient = FakeRoomClient().apply {
            getRoomHandler = { Result.failure(IOException("offline")) }
        }
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)

        val result = coordinator.restoreFromBootstrap(
            sessionBootstrap(currentRoomId = "room-1")
        )

        assertTrue(result.isSuccess)
        assertEquals("room-1", coordinator.state.value.currentRoom?.id)
        assertFalse(coordinator.state.value.isConnected)
        assertTrue(coordinator.state.value.isStale)
        assertEquals(PresenceState.IN_ROOM, presence.desiredStates.last())
    }

    @Test
    fun nonRetryableRestoreFailureClearsPartialSession() = runTest {
        val roomClient = FakeRoomClient().apply {
            getRoomHandler = {
                Result.failure(
                    ApiException(
                        httpCode = 401,
                        apiError = ApiErrorResponse(
                            code = "UNAUTHORIZED",
                            message = "Authentication required"
                        )
                    )
                )
            }
        }
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)

        val restore = coordinator.restoreFromBootstrap(
            sessionBootstrap(currentRoomId = "room-1")
        )
        val create = coordinator.createRoom(
            visibility = RoomVisibility.PRIVATE
        )

        assertTrue(restore.isFailure)
        assertTrue(create.isFailure)
        assertEquals(0, roomClient.createCalls)
        assertTrue(coordinator.state.value.rooms.isEmpty())
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(null, presence.desiredStates.last())
    }

    @Test
    fun cancelledRestoreClearsPartialSession() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        val roomResponse = CompletableDeferred<Result<Room>>()
        roomClient.getRoomHandler = { roomResponse.await() }

        val restore = async {
            coordinator.restoreFromBootstrap(
                sessionBootstrap(currentRoomId = "room-1")
            )
        }
        runCurrent()
        restore.cancel()
        runCurrent()

        assertTrue(restore.isCancelled)
        assertTrue(coordinator.state.value.rooms.isEmpty())
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(null, presence.desiredStates.last())
    }

    @Test
    fun createCommitsRoomBeforeChangingDesiredPresence() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        lateinit var coordinator: DefaultRoomSessionCoordinator
        presence.onDesiredState = { desired ->
            if (desired == PresenceState.IN_ROOM) {
                assertEquals("created-room", coordinator.state.value.currentRoom?.id)
            }
        }
        coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        roomClient.createResult = Result.success(room("created-room"))
        roomClient.membersHandler = { roomId ->
            Result.success(
                listOf(member(roomId).copy(role = RoomMemberRole.OWNER))
            )
        }

        val result = coordinator.createRoom(
            name = "Room",
            visibility = RoomVisibility.PRIVATE
        )

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().isBusy)
        assertTrue(result.getOrThrow().activeOperations.isEmpty())
        assertEquals("created-room", coordinator.state.value.currentRoom?.id)
        assertEquals(
            RoomMemberRole.OWNER,
            coordinator.state.value.currentRoom?.currentUserRole
        )
        assertEquals(1L, coordinator.state.value.currentRoom?.activeMemberCount)
        assertEquals(PresenceState.IN_ROOM, presence.desiredStates.last())
    }

    @Test
    fun leaveClearsRoomBeforeChangingDesiredPresence() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator =
            DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap("room-1")).getOrThrow()

        presence.onDesiredState = { desired ->
            if (desired == PresenceState.ONLINE) {
                assertNull(coordinator.state.value.currentRoom)
                assertTrue(coordinator.state.value.activeMembers.isEmpty())
            }
        }

        val result = coordinator.leaveCurrentRoom()

        assertTrue(result.isSuccess)
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(PresenceState.ONLINE, presence.desiredStates.last())
    }

    @Test
    fun simultaneousJoinIsRejectedBeforeSecondNetworkRequest() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        val releaseJoin = CompletableDeferred<Unit>()
        roomClient.joinHandler = {
            releaseJoin.await()
            Result.success(room(it))
        }

        val first = async { coordinator.joinLocalDiscoveryRoom("room-1") }
        runCurrent()
        val second = coordinator.joinLocalDiscoveryRoom("room-1")

        assertTrue(second.isFailure)
        assertEquals(1, roomClient.joinCalls)

        releaseJoin.complete(Unit)
        assertTrue(first.await().isSuccess)
    }

    @Test
    fun openRoomSwitchesServerCurrentRoomBeforeCommittingState() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        lateinit var coordinator: DefaultRoomSessionCoordinator
        presence.onDesiredState = { desired ->
            if (desired == PresenceState.IN_ROOM) {
                assertEquals("room-2", coordinator.state.value.currentRoom?.id)
            }
        }
        coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()

        val result = coordinator.openRoom("room-2")

        assertTrue(result.isSuccess)
        assertEquals(1, roomClient.joinCalls)
        assertEquals("room-2", coordinator.state.value.currentRoom?.id)
        assertEquals(1L, coordinator.state.value.currentRoom?.activeMemberCount)
        assertEquals(PresenceState.IN_ROOM, presence.desiredStates.last())
    }

    @Test
    fun openingAlreadyCurrentRoomRefreshesWithoutAnotherJoin() = runTest {
        val roomClient = FakeRoomClient()
        val coordinator = DefaultRoomSessionCoordinator(
            roomClient,
            FakePresenceCoordinator()
        )
        coordinator.restoreFromBootstrap(sessionBootstrap("room-1")).getOrThrow()
        val initialJoinCalls = roomClient.joinCalls

        val result = coordinator.openRoom("room-1")

        assertTrue(result.isSuccess)
        assertEquals(initialJoinCalls, roomClient.joinCalls)
        assertEquals("room-1", coordinator.state.value.currentRoom?.id)
    }

    @Test
    fun lifecycleJoinIsRejectedWhileOpenRoomSwitchIsInFlight() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        val releaseOpen = CompletableDeferred<Unit>()
        roomClient.joinHandler = {
            releaseOpen.await()
            Result.success(room(it))
        }

        val open = async { coordinator.openRoom("room-a") }
        runCurrent()

        val join = coordinator.joinLocalDiscoveryRoom("room-b")

        assertTrue(join.isFailure)
        assertEquals(1, roomClient.joinCalls)

        releaseOpen.complete(Unit)
        assertTrue(open.await().isSuccess)
        assertEquals("room-a", coordinator.state.value.currentRoom?.id)
        assertEquals(PresenceState.IN_ROOM, presence.desiredStates.last())
    }

    @Test
    fun openRoomIsRejectedWhileLifecycleJoinIsInFlight() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        val releaseJoin = CompletableDeferred<Unit>()
        roomClient.joinHandler = {
            releaseJoin.await()
            Result.success(room(it))
        }

        val join = async { coordinator.joinLocalDiscoveryRoom("room-b") }
        runCurrent()

        val open = coordinator.openRoom("room-a")

        assertTrue(open.isFailure)
        assertEquals(1, roomClient.joinCalls)
        releaseJoin.complete(Unit)
        assertTrue(join.await().isSuccess)
        assertEquals("room-b", coordinator.state.value.currentRoom?.id)
        assertEquals(PresenceState.IN_ROOM, presence.desiredStates.last())
    }

    @Test
    fun duplicateOpenDoesNotInvalidateFirstServerSwitch() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        val releaseOpen = CompletableDeferred<Unit>()
        roomClient.joinHandler = {
            releaseOpen.await()
            Result.success(room(it))
        }

        val first = async { coordinator.openRoom("room-a") }
        runCurrent()
        val duplicate = coordinator.openRoom("room-a")

        assertTrue(duplicate.isFailure)
        assertEquals(1, roomClient.joinCalls)

        releaseOpen.complete(Unit)
        assertTrue(first.await().isSuccess)
        assertEquals("room-a", coordinator.state.value.currentRoom?.id)
    }

    @Test
    fun roomsRefreshClearsCurrentRoomBeforeChangingDesiredPresence() = runTest {
        val roomClient = FakeRoomClient().apply {
            roomsResult = Result.success(emptyList())
        }
        val presence = FakePresenceCoordinator()
        val coordinator =
            DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap("room-1")).getOrThrow()
        presence.onDesiredState = { desired ->
            if (desired == PresenceState.ONLINE) {
                assertNull(coordinator.state.value.currentRoom)
                assertTrue(coordinator.state.value.activeMembers.isEmpty())
            }
        }

        val result = coordinator.loadRooms()

        assertTrue(result.isSuccess)
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(PresenceState.ONLINE, presence.desiredStates.last())
    }

    @Test
    fun archivedRoomSnapshotDuringRestoreClearsCurrentRoomAndPresence() = runTest {
        val roomClient = FakeRoomClient().apply {
            getRoomHandler = { roomId ->
                Result.success(
                    room(roomId).copy(
                        status = RoomStatus.ARCHIVED,
                        archivedAt = "2026-07-31T13:00:00"
                    )
                )
            }
        }
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)

        val result = coordinator.restoreFromBootstrap(sessionBootstrap("room-1"))

        assertTrue(result.isSuccess)
        assertNull(coordinator.state.value.currentRoom)
        assertTrue(coordinator.state.value.activeMembers.isEmpty())
        assertEquals(PresenceState.ONLINE, presence.desiredStates.last())
    }

    @Test
    fun unavailableMemberSnapshotDuringRestoreClearsCurrentRoom() = runTest {
        val roomClient = FakeRoomClient().apply {
            membersHandler = {
                Result.failure(
                    ApiException(
                        httpCode = 409,
                        apiError = ApiErrorResponse(
                            code = "ROOM_ARCHIVED",
                            message = "Room is archived"
                        )
                    )
                )
            }
        }
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)

        val result = coordinator.restoreFromBootstrap(sessionBootstrap("room-1"))

        assertTrue(result.isSuccess)
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(PresenceState.ONLINE, presence.desiredStates.last())
        assertEquals(
            "ROOM_ARCHIVED",
            coordinator.state.value.lastError?.apiCode
        )
    }

    @Test
    fun logoutInvalidatesInFlightLifecycleResponse() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        val createResponse = CompletableDeferred<Result<Room>>()
        roomClient.createHandler = { createResponse.await() }

        val create = async {
            coordinator.createRoom(visibility = RoomVisibility.PRIVATE)
        }
        runCurrent()

        coordinator.clearForLogout()
        createResponse.complete(Result.success(room("late-room")))

        assertTrue(create.await().isFailure)
        assertTrue(coordinator.state.value.rooms.isEmpty())
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(null, presence.desiredStates.last())
    }

    @Test
    fun lifecycleLoggingCapturesOperationAndPresenceWithoutSensitiveValues() = runTest {
        val roomClient = FakeRoomClient().apply {
            createResult = Result.success(
                room("created-room").copy(name = "Sensitive customer room")
            )
        }
        val logger = RecordingAppLogger()
        val coordinator = DefaultRoomSessionCoordinator(
            roomClient = roomClient,
            presenceCoordinator = FakePresenceCoordinator(),
            logger = logger
        )
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        logger.clear()

        val result = coordinator.createRoom(
            name = "Sensitive customer room",
            visibility = RoomVisibility.PRIVATE
        )

        assertTrue(result.isSuccess)
        assertTrue(
            logger.debugMessages.any {
                it.contains("Room session operation started") &&
                    it.contains("operation=CREATE")
            }
        )
        assertTrue(
            logger.debugMessages.any {
                it.contains("Desired Presence updated after room enter") &&
                    it.contains("desiredState=IN_ROOM")
            }
        )
        assertTrue(
            logger.infoMessages.any {
                it.contains("Room create committed") &&
                    it.contains("roomId=created-room") &&
                    it.contains("desiredPresence=IN_ROOM")
            }
        )

        val allMessages = logger.allMessages()
        assertTrue(allMessages.none { it.contains("Sensitive customer room") })
        assertTrue(allMessages.none { it.contains("owner-1") })
        assertTrue(allMessages.none { it.contains("device-1") })
        assertTrue(allMessages.none { it.contains("user-1") })
    }

    @Test
    fun logoutDuringPostEnterMemberRefreshInvalidatesOperation() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        val membersResponse = CompletableDeferred<Result<List<RoomMember>>>()
        roomClient.membersHandler = { membersResponse.await() }

        val create = async {
            coordinator.createRoom(visibility = RoomVisibility.PRIVATE)
        }
        runCurrent()
        assertEquals("created-room", coordinator.state.value.currentRoom?.id)

        coordinator.clearForLogout()
        membersResponse.complete(Result.success(listOf(member("created-room"))))

        assertTrue(create.await().isFailure)
        assertTrue(coordinator.state.value.rooms.isEmpty())
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(null, presence.desiredStates.last())
    }

    @Test
    fun unavailableMemberSnapshotAfterEnterFailsWithoutMisleadingCommitLog() = runTest {
        val roomClient = FakeRoomClient().apply {
            membersHandler = {
                Result.failure(
                    ApiException(
                        httpCode = 409,
                        apiError = ApiErrorResponse(
                            code = "ROOM_ARCHIVED",
                            message = "Room is archived"
                        )
                    )
                )
            }
        }
        val presence = FakePresenceCoordinator()
        val logger = RecordingAppLogger()
        val coordinator = DefaultRoomSessionCoordinator(
            roomClient = roomClient,
            presenceCoordinator = presence,
            logger = logger
        )
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        logger.clear()

        val result = coordinator.createRoom(
            name = "Room",
            visibility = RoomVisibility.PRIVATE
        )

        assertTrue(result.isFailure)
        assertNull(coordinator.state.value.currentRoom)
        assertTrue(coordinator.state.value.activeMembers.isEmpty())
        assertEquals(PresenceState.ONLINE, presence.desiredStates.last())
        assertTrue(
            logger.infoMessages.none {
                it.contains("Room create committed")
            }
        )
    }

    @Test
    fun retryableRestoreFailureAfterLogoutDoesNotReportSuccess() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        val roomResponse = CompletableDeferred<Result<Room>>()
        roomClient.getRoomHandler = { roomResponse.await() }

        val restore = async {
            coordinator.restoreFromBootstrap(sessionBootstrap("room-1"))
        }
        runCurrent()

        coordinator.clearForLogout()
        roomResponse.complete(Result.failure(IOException("offline")))

        assertTrue(restore.await().isFailure)
        assertTrue(coordinator.state.value.rooms.isEmpty())
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(null, presence.desiredStates.last())
    }

    @Test
    fun operationsAreRejectedAfterLogoutUntilNextBootstrap() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap(null)).getOrThrow()
        coordinator.clearForLogout()

        val result = coordinator.createRoom(
            visibility = RoomVisibility.PRIVATE
        )

        assertTrue(result.isFailure)
        assertEquals(0, roomClient.createCalls)
        assertTrue(coordinator.state.value.rooms.isEmpty())
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(null, presence.desiredStates.last())
    }

    @Test
    fun retryableMemberRestoreFailureSelectsRoomMissingFromSummary() = runTest {
        val roomClient = FakeRoomClient().apply {
            membersHandler = { Result.failure(IOException("offline")) }
        }
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        val bootstrap = sessionBootstrap("room-1").copy(rooms = emptyList())

        val result = coordinator.restoreFromBootstrap(bootstrap)

        assertTrue(result.isSuccess)
        assertEquals("room-1", coordinator.state.value.currentRoom?.id)
        assertEquals(listOf("room-1"), coordinator.state.value.rooms.map { it.id })
        assertFalse(coordinator.state.value.isConnected)
        assertTrue(coordinator.state.value.isStale)
        assertEquals(PresenceState.IN_ROOM, presence.desiredStates.last())
    }

    @Test
    fun reconnectRejectionKeepsFreshNonCurrentRooms() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap("room-1")).getOrThrow()
        roomClient.roomsResult = Result.success(
            listOf(room("room-1"), room("room-2"))
        )
        roomClient.getRoomHandler = {
            Result.failure(
                ApiException(
                    httpCode = 409,
                    apiError = ApiErrorResponse(
                        code = "ROOM_ARCHIVED",
                        message = "Room is archived"
                    )
                )
            )
        }

        val result = coordinator.reconnect()

        assertTrue(result.isSuccess)
        assertNull(coordinator.state.value.currentRoom)
        assertEquals(listOf("room-2"), coordinator.state.value.rooms.map { it.id })
        assertEquals(PresenceState.ONLINE, presence.desiredStates.last())
    }

    @Test
    fun refreshLoggingCapturesRoomAndMemberSnapshotSuccess() = runTest {
        val logger = RecordingAppLogger()
        val coordinator = DefaultRoomSessionCoordinator(
            roomClient = FakeRoomClient(),
            presenceCoordinator = FakePresenceCoordinator(),
            logger = logger
        )
        coordinator.restoreFromBootstrap(sessionBootstrap("room-1")).getOrThrow()
        logger.clear()

        assertTrue(coordinator.refreshCurrentRoom().isSuccess)
        assertTrue(coordinator.refreshActiveMembers().isSuccess)

        assertTrue(
            logger.debugMessages.any {
                it.contains("Current room details refreshed") &&
                    it.contains("roomId=room-1")
            }
        )
        assertTrue(
            logger.debugMessages.any {
                it.contains("Active room members refreshed") &&
                    it.contains("roomId=room-1") &&
                    it.contains("memberCount=1")
            }
        )
    }

    @Test
    fun reconnectRetainsStateWhileOfflineThenRefreshesServerSnapshot() = runTest {
        val roomClient = FakeRoomClient()
        val presence = FakePresenceCoordinator()
        val coordinator = DefaultRoomSessionCoordinator(roomClient, presence)
        coordinator.restoreFromBootstrap(sessionBootstrap("room-1")).getOrThrow()

        coordinator.markDisconnected()

        assertFalse(coordinator.state.value.isConnected)
        assertTrue(coordinator.state.value.isStale)
        assertEquals("room-1", coordinator.state.value.currentRoom?.id)

        roomClient.roomsResult = Result.success(
            listOf(room("room-1").copy(name = "Updated room"))
        )
        roomClient.getRoomHandler = {
            Result.success(room(it).copy(name = "Updated room"))
        }

        val result = coordinator.reconnect()

        assertTrue(result.isSuccess)
        assertTrue(coordinator.state.value.isConnected)
        assertFalse(coordinator.state.value.isStale)
        assertEquals("Updated room", coordinator.state.value.currentRoom?.name)
    }

    private class FakeRoomClient : RoomClient {
        var roomsResult: Result<List<Room>> = Result.success(listOf(room("room-1")))
        var createResult: Result<Room> = Result.success(room("created-room"))
        var createHandler: suspend () -> Result<Room> = { createResult }
        var getRoomHandler: suspend (String) -> Result<Room> = {
            Result.success(room(it))
        }
        var membersHandler: suspend (String) -> Result<List<RoomMember>> = {
            Result.success(listOf(member(it)))
        }
        var joinHandler: suspend (String) -> Result<Room> = {
            Result.success(room(it))
        }
        var createCalls: Int = 0
        var joinCalls: Int = 0

        override suspend fun createRoom(
            name: String?,
            visibility: RoomVisibility
        ): Result<Room> {
            createCalls += 1
            return createHandler()
        }

        override suspend fun getRooms(): Result<List<Room>> = roomsResult

        override suspend fun getRoom(roomId: String): Result<Room> =
            getRoomHandler(roomId)

        override suspend fun getActiveMembers(
            roomId: String
        ): Result<List<RoomMember>> = membersHandler(roomId)

        override suspend fun joinLocalDiscoveryRoom(roomId: String): Result<Room> {
            joinCalls += 1
            return joinHandler(roomId)
        }

        override suspend fun activateRoom(roomId: String): Result<Room> =
            Result.success(room(roomId))

        override suspend fun deactivateRoom(roomId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun leaveRoom(roomId: String): Result<Room> =
            Result.success(room(roomId))

        override suspend fun archiveRoom(roomId: String): Result<Room> =
            Result.success(
                room(roomId).copy(
                    status = RoomStatus.ARCHIVED,
                    archivedAt = "2026-07-31T13:00:00"
                )
            )
    }

    private class RecordingAppLogger : AppLogger {
        val debugMessages = mutableListOf<String>()
        val infoMessages = mutableListOf<String>()
        val warningMessages = mutableListOf<String>()
        val errorMessages = mutableListOf<String>()

        override fun debug(tag: String, message: String) {
            debugMessages += "$tag: $message"
        }

        override fun info(tag: String, message: String) {
            infoMessages += "$tag: $message"
        }

        override fun warn(
            tag: String,
            message: String,
            throwable: Throwable?
        ) {
            warningMessages += "$tag: $message"
        }

        override fun error(
            tag: String,
            message: String,
            throwable: Throwable?
        ) {
            errorMessages += "$tag: $message"
        }

        fun allMessages(): List<String> =
            debugMessages + infoMessages + warningMessages + errorMessages

        fun clear() {
            debugMessages.clear()
            infoMessages.clear()
            warningMessages.clear()
            errorMessages.clear()
        }
    }

    private class FakePresenceCoordinator : PresenceHeartbeatCoordinator {
        override val confirmedPresence: StateFlow<PresenceSnapshot?> =
            MutableStateFlow(null)
        override val desiredState: StateFlow<PresenceState?> =
            MutableStateFlow(null)
        val desiredStates = mutableListOf<PresenceState?>()
        var onDesiredState: (PresenceState?) -> Unit = {}

        override fun start(immediate: Boolean) = Unit
        override fun stop() = Unit
        override fun setDesiredState(state: PresenceState?) {
            desiredStates += state
            onDesiredState(state)
        }
        override suspend fun heartbeatNow(): Result<PresenceSnapshot> =
            Result.success(
                PresenceSnapshot(
                    userId = "user-1",
                    deviceId = "device-1",
                    state = PresenceState.ONLINE,
                    currentRoomId = null,
                    lastSeenAt = "2026-07-31T12:00:00"
                )
            )
    }

    private companion object {
        fun sessionBootstrap(
            currentRoomId: String?,
            roomName: String? = "Room 1"
        ): SessionBootstrapResponse =
            SessionBootstrapResponse(
                rooms = listOf(
                    RoomSummaryResponse(
                        id = "room-1",
                        ownerUserId = "owner-1",
                        ownerDeviceId = "device-1",
                        name = roomName,
                        status = RoomStatus.ACTIVE,
                        visibility = RoomVisibility.LOCAL_DISCOVERY,
                        currentUserRole = RoomMemberRole.MEMBER,
                        activeMemberCount = 2,
                        createdAt = "2026-07-31T12:00:00",
                        updatedAt = "2026-07-31T12:00:00",
                        archivedAt = null
                    )
                ),
                presence = SessionPresenceResponse(
                    userId = "user-1",
                    state = if (currentRoomId == null) {
                        PresenceState.ONLINE
                    } else {
                        PresenceState.IN_ROOM
                    },
                    currentRoomId = currentRoomId,
                    lastSeenAt = "2026-07-31T12:00:00"
                )
            )

        fun room(
            id: String,
            name: String? = "Room $id"
        ): Room = Room(
            id = id,
            ownerUserId = "owner-1",
            ownerDeviceId = "device-1",
            name = name,
            status = RoomStatus.ACTIVE,
            visibility = RoomVisibility.LOCAL_DISCOVERY,
            createdAt = "2026-07-31T12:00:00",
            updatedAt = "2026-07-31T12:00:00",
            archivedAt = null
        )

        fun member(roomId: String): RoomMember = RoomMember(
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
    }
}
