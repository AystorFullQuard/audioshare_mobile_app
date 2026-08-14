package mme.corp.audioshare.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.api.RoomsApi
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomMemberState
import mme.corp.audioshare.data.dto.room.RoomStatus
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.exception.DeviceBootstrapRequiredException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.testutil.FakeDeviceIdStore
import mme.corp.audioshare.testutil.retrofit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RoomRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun createRoomUsesStoredDeviceIdAndMapsResponse() = runTest {
        server.enqueue(roomResponse(statusCode = 201, visibility = "LOCAL_DISCOVERY"))
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.createRoom(
            name = "AudioShare Room",
            visibility = RoomVisibility.LOCAL_DISCOVERY
        )

        val room = result.getOrThrow()
        assertEquals("room-id", room.id)
        assertEquals(RoomStatus.ACTIVE, room.status)
        assertEquals(RoomVisibility.LOCAL_DISCOVERY, room.visibility)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/rooms", request.path)

        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("device-id", body["ownerDeviceId"].asString)
        assertEquals("AudioShare Room", body["name"].asString)
        assertEquals("LOCAL_DISCOVERY", body["visibility"].asString)
    }

    @Test
    fun listRoomsMapsAllRoomSnapshots() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """
                [
                  ${roomJson(roomId = "room-1")},
                  ${roomJson(roomId = "room-2", visibility = "PRIVATE")}
                ]
                """.trimIndent()
            )
        )
        val repository = repository(FakeDeviceIdStore("device-id"))

        val rooms = repository.getRooms().getOrThrow()

        assertEquals(listOf("room-1", "room-2"), rooms.map { it.id })
        assertEquals("/api/v1/rooms", server.takeRequest().path)
    }

    @Test
    fun getRoomUsesRoomEndpoint() = runTest {
        server.enqueue(roomResponse())
        val repository = repository(FakeDeviceIdStore("device-id"))

        val room = repository.getRoom("room-id").getOrThrow()

        assertEquals("room-id", room.id)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/rooms/room-id", request.path)
    }

    @Test
    fun activeMembersMapIdentityMembershipAndPresenceSnapshots() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """
                [
                  {
                    "id": "membership-owner",
                    "roomId": "room-id",
                    "userId": "owner-user-id",
                    "username": "owner",
                    "displayName": "Owner Pixel 8",
                    "avatarURL": null,
                    "role": "OWNER",
                    "state": "ACTIVE",
                    "presenceState": "IN_ROOM",
                    "presenceLastSeenAt": "2026-07-31T12:30:00",
                    "joinedAt": "2026-07-31T12:00:00",
                    "membershipLastSeenAt": "2026-07-31T12:30:00",
                    "leftAt": null
                  },
                  {
                    "id": "membership-member",
                    "roomId": "room-id",
                    "userId": "member-user-id",
                    "username": "member",
                    "displayName": "Member Samsung S24",
                    "avatarURL": null,
                    "role": "MEMBER",
                    "state": "ACTIVE",
                    "presenceState": "OFFLINE",
                    "presenceLastSeenAt": null,
                    "joinedAt": "2026-07-31T12:05:00",
                    "membershipLastSeenAt": "2026-07-31T12:25:00",
                    "leftAt": null
                  }
                ]
                """.trimIndent()
            )
        )
        val repository = repository(FakeDeviceIdStore("device-id"))

        val members = repository.getActiveMembers("room-id").getOrThrow()

        assertEquals(2, members.size)
        assertEquals(RoomMemberRole.OWNER, members[0].role)
        assertEquals(RoomMemberState.ACTIVE, members[1].membershipState)
        assertEquals(PresenceState.OFFLINE, members[1].presenceState)
        assertNull(members[1].presenceLastSeenAt)
        assertEquals("/api/v1/rooms/room-id/members", server.takeRequest().path)
    }

    @Test
    fun joinLeaveAndArchiveSendStoredDeviceId() = runTest {
        repeat(3) { server.enqueue(roomResponse()) }
        val repository = repository(FakeDeviceIdStore("device-id"))

        assertTrue(repository.joinLocalDiscoveryRoom("room-id").isSuccess)
        assertTrue(repository.leaveRoom("room-id").isSuccess)
        assertTrue(repository.archiveRoom("room-id").isSuccess)

        val expectedPaths = listOf(
            "/api/v1/rooms/room-id/join",
            "/api/v1/rooms/room-id/leave",
            "/api/v1/rooms/room-id/archive"
        )

        expectedPaths.forEach { expectedPath ->
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals(expectedPath, request.path)
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals("device-id", body["deviceId"].asString)
        }
    }

    @Test
    fun activateRoomUsesEndpointDeviceIdAndMapsResponse() = runTest {
        server.enqueue(roomResponse())
        val repository = repository(FakeDeviceIdStore("device-id"))

        val room = repository.activateRoom(" room-id ").getOrThrow()

        assertEquals("room-id", room.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/rooms/room-id/activate", request.path)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("device-id", body["deviceId"].asString)
    }

    @Test
    fun deactivateRoomAcceptsNoContentAndSendsDeviceId() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.deactivateRoom("room-id")

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/rooms/room-id/deactivate", request.path)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("device-id", body["deviceId"].asString)
    }

    @Test
    fun activateRoomRejectsEmptySuccessfulResponse() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.activateRoom("room-id")

        assertTrue(result.isFailure)
        assertEquals(
            "/api/v1/rooms/room-id/activate",
            server.takeRequest().path
        )
    }

    @Test
    fun activateRoomMapsNullResponseToEmptyBodyFailure() = runTest {
        server.enqueue(jsonResponse(200, "null"))
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.activateRoom("room-id")

        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
        assertEquals(
            "Activate room response body is empty",
            exception?.message
        )
    }

    @Test
    fun activateRoomReturnsFailureForMalformedResponse() = runTest {
        server.enqueue(jsonResponse(200, "{not-json"))
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.activateRoom("room-id")

        assertTrue(result.isFailure)
        assertEquals(
            "/api/v1/rooms/room-id/activate",
            server.takeRequest().path
        )
    }

    @Test
    fun activationAndDeactivationMapServeRelayErrors() = runTest {
        server.enqueue(apiErrorResponse(403, "ROOM_MEMBER_REQUIRED"))
        server.enqueue(apiErrorResponse(403, "DEVICE_NOT_OWNED"))
        val repository = repository(FakeDeviceIdStore("device-id"))

        val activateError = repository.activateRoom("room-id")
            .exceptionOrNull() as ApiException
        val deactivateError = repository.deactivateRoom("room-id")
            .exceptionOrNull() as ApiException

        assertEquals(403, activateError.httpCode)
        assertEquals("ROOM_MEMBER_REQUIRED", activateError.apiError.code)
        assertEquals(403, deactivateError.httpCode)
        assertEquals("DEVICE_NOT_OWNED", deactivateError.apiError.code)
    }

    @Test
    fun activationOperationsValidateInputsBeforeNetworkCall() = runTest {
        val missingDeviceRepository = repository(FakeDeviceIdStore())
        val blankRoomRepository = repository(FakeDeviceIdStore("device-id"))

        val missingDevice = missingDeviceRepository.activateRoom("room-id")
        val blankActivate = blankRoomRepository.activateRoom("  ")
        val blankDeactivate = blankRoomRepository.deactivateRoom("  ")

        assertTrue(
            missingDevice.exceptionOrNull() is DeviceBootstrapRequiredException
        )
        assertTrue(blankActivate.exceptionOrNull() is IllegalArgumentException)
        assertTrue(blankDeactivate.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun activationRethrowsDeviceStoreCancellation() = runTest {
        val expected = CancellationException("cancelled")
        val repository = repository(
            object : DeviceIdStore {
                override suspend fun getDeviceId(): String = throw expected

                override suspend fun saveDeviceId(deviceId: String) = Unit
            }
        )

        try {
            repository.activateRoom("room-id")
            fail("CancellationException must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun deviceActionWithoutDeviceIdFailsBeforeNetworkCall() = runTest {
        val logger = RecordingAppLogger()
        val repository = repository(FakeDeviceIdStore(), logger)

        val result = repository.joinLocalDiscoveryRoom("room-id")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeviceBootstrapRequiredException)
        assertEquals(0, server.requestCount)
        assertTrue(
            logger.warningMessages.any {
                it.contains("Room join failed") &&
                    it.contains("DeviceBootstrapRequiredException")
            }
        )
        assertTrue(logger.errorMessages.isEmpty())
    }

    @Test
    fun blankRoomIdFailsBeforeNetworkCall() = runTest {
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.getRoom("  ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mapsServeRelayRoomApiErrorAndLogsStableCodes() = runTest {
        server.enqueue(
            jsonResponse(
                409,
                """
                {
                  "code": "ROOM_ARCHIVED",
                  "message": "Room is archived",
                  "fieldErrors": {},
                  "timestamp": "2026-07-31T12:00:00"
                }
                """.trimIndent()
            )
        )
        val logger = RecordingAppLogger()
        val repository = repository(FakeDeviceIdStore("device-id"), logger)

        val result = repository.joinLocalDiscoveryRoom("room-id")

        val exception = result.exceptionOrNull() as ApiException
        assertEquals(409, exception.httpCode)
        assertEquals("ROOM_ARCHIVED", exception.apiError.code)
        assertTrue(
            logger.warningMessages.any {
                it.contains("httpCode=409") &&
                    it.contains("apiCode=ROOM_ARCHIVED")
            }
        )
        assertTrue(logger.errorMessages.isEmpty())
    }

    @Test
    fun createRoomLogsLifecycleWithoutSensitiveRequestValues() = runTest {
        server.enqueue(roomResponse(statusCode = 201))
        val logger = RecordingAppLogger()
        val repository = repository(FakeDeviceIdStore("device-id"), logger)

        val result = repository.createRoom(
            name = "Private customer room",
            visibility = RoomVisibility.PRIVATE
        )

        assertTrue(result.isSuccess)
        assertTrue(
            logger.debugMessages.any {
                it.contains("Room create started") &&
                    it.contains("visibility=PRIVATE") &&
                    it.contains("hasCustomName=true")
            }
        )
        assertTrue(
            logger.infoMessages.any {
                it.contains("Room create succeeded") &&
                    it.contains("roomId=room-id")
            }
        )

        val allMessages = logger.allMessages()
        assertTrue(allMessages.none { it.contains("device-id") })
        assertTrue(allMessages.none { it.contains("Private customer room") })
    }

    @Test
    fun unnamedRoomResponseMapsSuccessfully() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """
                {
                  "id": "room-id",
                  "ownerUserId": "owner-user-id",
                  "ownerDeviceId": "device-id",
                  "name": null,
                  "status": "ACTIVE",
                  "visibility": "LOCAL_DISCOVERY",
                  "createdAt": "2026-07-31T12:00:00",
                  "updatedAt": "2026-07-31T12:00:00",
                  "archivedAt": null
                }
                """.trimIndent()
            )
        )
        val logger = RecordingAppLogger()
        val repository = repository(FakeDeviceIdStore("device-id"), logger)

        val result = repository.getRoom("room-id")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().name)
        assertTrue(logger.errorMessages.isEmpty())
    }

    @Test
    fun optionalCreateRoomNameIsOmittedFromJson() = runTest {
        server.enqueue(roomResponse())
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.createRoom(visibility = RoomVisibility.PRIVATE)

        assertTrue(result.isSuccess)
        val body = JsonParser.parseString(
            server.takeRequest().body.readUtf8()
        ).asJsonObject
        assertFalse(body.has("name"))
    }

    private fun repository(
        store: DeviceIdStore,
        logger: AppLogger = AppLogger.NO_OP
    ): RoomRepository {
        val api = server.retrofit().create(RoomsApi::class.java)
        return RoomRepository(api, store, logger)
    }

    private fun roomResponse(
        statusCode: Int = 200,
        visibility: String = "LOCAL_DISCOVERY"
    ): MockResponse = jsonResponse(
        statusCode,
        roomJson(visibility = visibility)
    )

    private fun roomJson(
        roomId: String = "room-id",
        visibility: String = "LOCAL_DISCOVERY"
    ): String =
        """
        {
          "id": "$roomId",
          "ownerUserId": "owner-user-id",
          "ownerDeviceId": "device-id",
          "name": "AudioShare Room",
          "status": "ACTIVE",
          "visibility": "$visibility",
          "createdAt": "2026-07-31T12:00:00",
          "updatedAt": "2026-07-31T12:00:00",
          "archivedAt": null
        }
        """.trimIndent()

    private fun jsonResponse(code: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun apiErrorResponse(code: Int, apiCode: String): MockResponse =
        jsonResponse(
            code,
            """
            {
              "code": "$apiCode",
              "message": "Room operation failed",
              "fieldErrors": {},
              "timestamp": "2026-08-04T18:00:00"
            }
            """.trimIndent()
        )

    private class RecordingAppLogger : AppLogger {
        val debugMessages = mutableListOf<String>()
        val infoMessages = mutableListOf<String>()
        val warningMessages = mutableListOf<String>()
        val errorMessages = mutableListOf<String>()
        val errorThrowables = mutableListOf<Throwable?>()

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
            errorThrowables += throwable
        }

        fun allMessages(): List<String> =
            debugMessages + infoMessages + warningMessages + errorMessages
    }
}
