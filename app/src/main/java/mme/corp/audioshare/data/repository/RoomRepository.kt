package mme.corp.audioshare.data.repository

import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.data.api.RoomsApi
import mme.corp.audioshare.data.dto.room.CreateRoomRequest
import mme.corp.audioshare.data.dto.room.RoomDeviceActionRequest
import mme.corp.audioshare.data.dto.room.RoomResponse
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.data.model.room.RoomMember
import mme.corp.audioshare.data.model.room.toDomain
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.exception.DeviceBootstrapRequiredException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.network.retrofit.executeApiCall
import mme.corp.audioshare.network.retrofit.executeApiCallWithoutBody
import retrofit2.Response

interface RoomClient {

    suspend fun createRoom(
        name: String? = null,
        visibility: RoomVisibility = RoomVisibility.PRIVATE
    ): Result<Room>

    suspend fun getRooms(): Result<List<Room>>

    suspend fun getRoom(roomId: String): Result<Room>

    suspend fun getActiveMembers(roomId: String): Result<List<RoomMember>>

    suspend fun joinLocalDiscoveryRoom(roomId: String): Result<Room>

    suspend fun activateRoom(roomId: String): Result<Room>

    suspend fun deactivateRoom(roomId: String): Result<Unit>

    suspend fun leaveRoom(roomId: String): Result<Room>

    suspend fun archiveRoom(roomId: String): Result<Room>
}

class RoomRepository(
    private val roomsApi: RoomsApi,
    private val deviceIdStore: DeviceIdStore,
    private val logger: AppLogger = AppLogger.NO_OP
) : RoomClient {

    override suspend fun createRoom(
        name: String?,
        visibility: RoomVisibility
    ): Result<Room> {
        logger.debug(
            TAG,
            "Room create started: visibility=${visibility.name}, " +
                "hasCustomName=${!name.isNullOrBlank()}"
        )

        val deviceId = requireDeviceId().getOrElse { exception ->
            logFailure(CREATE, null, exception)
            return Result.failure(exception)
        }

        return executeApiCall(
            emptyBodyMessage = "Create room response body is empty"
        ) {
            roomsApi.createRoom(
                CreateRoomRequest(
                    ownerDeviceId = deviceId,
                    name = name,
                    visibility = visibility
                )
            )
        }
            .mapSafely { response -> response.toDomain() }
            .logResult(CREATE, lifecycle = true) { room ->
                "roomId=${room.id}, visibility=${room.visibility}"
            }
    }

    override suspend fun getRooms(): Result<List<Room>> {
        logger.debug(TAG, "Room list started")

        return executeApiCall(
            emptyBodyMessage = "Rooms response body is empty"
        ) {
            roomsApi.getCurrentUserRooms()
        }
            .mapSafely { rooms ->
                rooms.map { response -> response.toDomain() }
            }
            .logResult(LIST) { rooms -> "roomCount=${rooms.size}" }
    }

    override suspend fun getRoom(roomId: String): Result<Room> {
        val resolvedRoomId = requireRoomId(roomId).getOrElse { exception ->
            logFailure(GET, roomId, exception)
            return Result.failure(exception)
        }

        logger.debug(TAG, "Room get started: roomId=$resolvedRoomId")

        return executeApiCall(
            emptyBodyMessage = "Room response body is empty"
        ) {
            roomsApi.getRoom(resolvedRoomId)
        }
            .mapSafely { response -> response.toDomain() }
            .logResult(GET, failureRoomId = resolvedRoomId) { room ->
                "roomId=${room.id}"
            }
    }

    override suspend fun getActiveMembers(roomId: String): Result<List<RoomMember>> {
        val resolvedRoomId = requireRoomId(roomId).getOrElse { exception ->
            logFailure(MEMBERS, roomId, exception)
            return Result.failure(exception)
        }

        logger.debug(
            TAG,
            "Room members load started: roomId=$resolvedRoomId"
        )

        return executeApiCall(
            emptyBodyMessage = "Room members response body is empty"
        ) {
            roomsApi.getActiveMembers(resolvedRoomId)
        }
            .mapSafely { members ->
                members.map { response -> response.toDomain() }
            }
            .logResult(
                operation = MEMBERS,
                failureRoomId = resolvedRoomId
            ) { members ->
                "roomId=$resolvedRoomId, memberCount=${members.size}"
            }
    }

    override suspend fun joinLocalDiscoveryRoom(roomId: String): Result<Room> =
        executeDeviceRoomAction(
            operation = JOIN,
            roomId = roomId,
            emptyBodyMessage = "Join room response body is empty",
            call = roomsApi::joinRoom
        )

    override suspend fun activateRoom(roomId: String): Result<Room> =
        executeDeviceRoomAction(
            operation = ACTIVATE,
            roomId = roomId,
            emptyBodyMessage = "Activate room response body is empty",
            call = roomsApi::activateRoom
        )

    override suspend fun deactivateRoom(roomId: String): Result<Unit> =
        executeDeviceRoomOperation(
            operation = DEACTIVATE,
            roomId = roomId,
            operationCall = { resolvedRoomId, request ->
                executeApiCallWithoutBody {
                    roomsApi.deactivateRoom(resolvedRoomId, request)
                }
            },
            successContext = { resolvedRoomId, _ ->
                "roomId=$resolvedRoomId"
            }
        )

    override suspend fun leaveRoom(roomId: String): Result<Room> =
        executeDeviceRoomAction(
            operation = LEAVE,
            roomId = roomId,
            emptyBodyMessage = "Leave room response body is empty",
            call = roomsApi::leaveRoom
        )

    override suspend fun archiveRoom(roomId: String): Result<Room> =
        executeDeviceRoomAction(
            operation = ARCHIVE,
            roomId = roomId,
            emptyBodyMessage = "Archive room response body is empty",
            call = roomsApi::archiveRoom
        )

    private suspend fun executeDeviceRoomAction(
        operation: String,
        roomId: String,
        emptyBodyMessage: String,
        call: suspend (
            String,
            RoomDeviceActionRequest
        ) -> Response<RoomResponse>
    ): Result<Room> = executeDeviceRoomOperation(
        operation = operation,
        roomId = roomId,
        operationCall = { resolvedRoomId, request ->
            executeApiCall(emptyBodyMessage) {
                call(resolvedRoomId, request)
            }.mapSafely { response -> response.toDomain() }
        },
        successContext = { _, room -> "roomId=${room.id}" }
    )

    private suspend fun <T> executeDeviceRoomOperation(
        operation: String,
        roomId: String,
        operationCall: suspend (
            String,
            RoomDeviceActionRequest
        ) -> Result<T>,
        successContext: (String, T) -> String
    ): Result<T> {
        val resolvedRoomId = requireRoomId(roomId).getOrElse { exception ->
            logFailure(operation, roomId, exception)
            return Result.failure(exception)
        }
        val deviceId = requireDeviceId().getOrElse { exception ->
            logFailure(operation, resolvedRoomId, exception)
            return Result.failure(exception)
        }

        logger.debug(
            TAG,
            "Room $operation started: roomId=$resolvedRoomId"
        )

        return operationCall(
            resolvedRoomId,
            RoomDeviceActionRequest(deviceId)
        )
            .logResult(
                operation = operation,
                lifecycle = true,
                failureRoomId = resolvedRoomId
            ) { value -> successContext(resolvedRoomId, value) }
    }

    private suspend fun requireDeviceId(): Result<String> {
        return try {
            val deviceId = deviceIdStore.getDeviceId()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return Result.failure(DeviceBootstrapRequiredException())

            Result.success(deviceId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun requireRoomId(roomId: String): Result<String> {
        val resolvedRoomId = roomId.trim()
        return if (resolvedRoomId.isEmpty()) {
            Result.failure(IllegalArgumentException("roomId is required"))
        } else {
            Result.success(resolvedRoomId)
        }
    }

    private inline fun <T, R> Result<T>.mapSafely(
        transform: (T) -> R
    ): Result<R> = fold(
        onSuccess = { value ->
            try {
                Result.success(transform(value))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        },
        onFailure = { exception -> Result.failure(exception) }
    )

    private inline fun <T> Result<T>.logResult(
        operation: String,
        lifecycle: Boolean = false,
        failureRoomId: String? = null,
        successContext: (T) -> String
    ): Result<T> = onSuccess { value ->
        val message = "Room $operation succeeded: ${successContext(value)}"
        if (lifecycle) {
            logger.info(TAG, message)
        } else {
            logger.debug(TAG, message)
        }
    }.onFailure { exception ->
        logFailure(operation, failureRoomId, exception)
    }

    private fun logFailure(
        operation: String,
        roomId: String?,
        exception: Throwable
    ) {
        val message = buildString {
            append("Room ")
            append(operation)
            append(" failed: ")
            if (!roomId.isNullOrBlank()) {
                append("roomId=")
                append(roomId.trim())
                append(", ")
            }
            append(exception.safeSummary())
        }

        when (exception) {
            is ApiException if exception.httpCode >= 500 ->
                logger.error(TAG, message)

            is ApiException, is DeviceBootstrapRequiredException, is IllegalArgumentException -> logger.warn(TAG, message)
            is IllegalStateException -> logger.error(TAG, message)
            else -> logger.error(TAG, message, exception)
        }
    }

    private fun Throwable.safeSummary(): String =
        if (this is ApiException) {
            "type=ApiException, httpCode=$httpCode, " +
                "apiCode=${apiError.code}"
        } else {
            "type=${safeTypeName()}"
        }

    private fun Throwable.safeTypeName(): String =
        this::class.java.simpleName.ifBlank { "Throwable" }

    private companion object {
        const val TAG = "RoomRepository"
        const val CREATE = "create"
        const val LIST = "list"
        const val GET = "get"
        const val MEMBERS = "members"
        const val JOIN = "join"
        const val ACTIVATE = "activate"
        const val DEACTIVATE = "deactivate"
        const val LEAVE = "leave"
        const val ARCHIVE = "archive"
    }
}
