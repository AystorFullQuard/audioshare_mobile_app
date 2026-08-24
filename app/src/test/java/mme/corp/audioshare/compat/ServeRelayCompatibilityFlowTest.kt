package mme.corp.audioshare.compat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.api.BootstrapApi
import mme.corp.audioshare.data.api.DevicesApi
import mme.corp.audioshare.data.api.PresenceApi
import mme.corp.audioshare.data.api.RoomsApi
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.dto.room.RoomMemberRole
import mme.corp.audioshare.data.dto.room.RoomVisibility
import mme.corp.audioshare.data.repository.BootstrapRepository
import mme.corp.audioshare.data.repository.DeviceRepository
import mme.corp.audioshare.data.repository.PresenceRepository
import mme.corp.audioshare.data.repository.RoomRepository
import mme.corp.audioshare.data.repository.SessionBootstrapMetadata
import mme.corp.audioshare.network.interceptor.AuthInterceptor
import mme.corp.audioshare.startup.DeviceRegistrationCoordinator
import mme.corp.audioshare.testutil.FakeDeviceIdStore
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Cross-project HTTP contract regression for the current ServeRelay API.
 *
 * Fixtures mirror the request/response shapes exposed by the ServeRelay
 * Devices, Session Bootstrap, Presence, and Rooms controllers. These tests are
 * intentionally broader than repository unit tests: they exercise the real
 * Retrofit annotations, Gson mapping, AuthInterceptor, repositories, device
 * persistence boundary, and the same device id across startup and room entry.
 */
class ServeRelayCompatibilityFlowTest {

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
    fun freshOwnerDeviceBootstrapPresenceAndRoomEntryMatchServeRelayContract() = runTest {
        enqueueDeviceRegistration(
            deviceId = OWNER_DEVICE_ID,
            userId = OWNER_USER_ID,
            deviceName = OWNER_DEVICE_NAME
        )
        enqueueSessionBootstrap(
            userId = OWNER_USER_ID,
            deviceId = OWNER_DEVICE_ID
        )
        enqueueHeartbeat(
            userId = OWNER_USER_ID,
            deviceId = OWNER_DEVICE_ID
        )
        enqueueRoomResponse()
        enqueueRoomResponse()
        enqueueRoomResponse()
        enqueueMembersResponse(includeMember = false)

        val harness = harness(OWNER_ACCESS_TOKEN)
        val metadata = metadata(OWNER_DEVICE_NAME)

        val resolvedDeviceId = harness.deviceRegistration
            .resolveOrRegister(metadata)
            .getOrThrow()
        assertEquals(OWNER_DEVICE_ID, resolvedDeviceId)
        assertEquals(OWNER_DEVICE_ID, harness.deviceStore.storedDeviceId)
        assertDeviceRegistrationRequest(
            request = server.takeRequest(),
            token = OWNER_ACCESS_TOKEN,
            deviceName = OWNER_DEVICE_NAME
        )

        val bootstrap = harness.bootstrap
            .loadSessionBootstrap(metadata)
            .getOrThrow()
        assertTrue(bootstrap.rooms.isEmpty())
        assertEquals(PresenceState.ONLINE, bootstrap.presence?.state)
        assertNull(bootstrap.presence?.currentRoomId)
        assertSessionBootstrapRequest(
            request = server.takeRequest(),
            token = OWNER_ACCESS_TOKEN,
            deviceId = OWNER_DEVICE_ID,
            deviceName = OWNER_DEVICE_NAME
        )

        val presence = harness.presence.heartbeat().getOrThrow()
        assertEquals(OWNER_DEVICE_ID, presence.deviceId)
        assertEquals(PresenceState.ONLINE, presence.state)
        assertNull(presence.currentRoomId)
        assertHeartbeatRequest(
            request = server.takeRequest(),
            token = OWNER_ACCESS_TOKEN,
            deviceId = OWNER_DEVICE_ID
        )

        val createdRoom = harness.rooms.createRoom(
            name = ROOM_NAME,
            visibility = RoomVisibility.LOCAL_DISCOVERY
        ).getOrThrow()
        assertEquals(ROOM_ID, createdRoom.id)
        assertEquals(RoomVisibility.LOCAL_DISCOVERY, createdRoom.visibility)
        assertOwnerCreateRoomRequest(server.takeRequest())

        val activatedRoom = harness.rooms.activateRoom(ROOM_ID).getOrThrow()
        assertEquals(ROOM_ID, activatedRoom.id)
        assertDeviceRoomActionRequest(
            request = server.takeRequest(),
            path = "/api/v1/rooms/$ROOM_ID/activate",
            token = OWNER_ACCESS_TOKEN,
            deviceId = OWNER_DEVICE_ID
        )

        val details = harness.rooms.getRoom(ROOM_ID).getOrThrow()
        assertEquals(ROOM_ID, details.id)
        assertSimpleAuthenticatedRequest(
            request = server.takeRequest(),
            method = "GET",
            path = "/api/v1/rooms/$ROOM_ID",
            token = OWNER_ACCESS_TOKEN
        )

        val members = harness.rooms.getActiveMembers(ROOM_ID).getOrThrow()
        assertEquals(1, members.size)
        assertEquals(RoomMemberRole.OWNER, members.single().role)
        assertEquals(PresenceState.IN_ROOM, members.single().presenceState)
        assertSimpleAuthenticatedRequest(
            request = server.takeRequest(),
            method = "GET",
            path = "/api/v1/rooms/$ROOM_ID/members",
            token = OWNER_ACCESS_TOKEN
        )

        assertEquals(7, server.requestCount)
    }

    @Test
    fun freshMemberDeviceBootstrapPresenceJoinAndRoomEntryMatchServeRelayContract() = runTest {
        enqueueDeviceRegistration(
            deviceId = MEMBER_DEVICE_ID,
            userId = MEMBER_USER_ID,
            deviceName = MEMBER_DEVICE_NAME
        )
        enqueueSessionBootstrap(
            userId = MEMBER_USER_ID,
            deviceId = MEMBER_DEVICE_ID
        )
        enqueueHeartbeat(
            userId = MEMBER_USER_ID,
            deviceId = MEMBER_DEVICE_ID
        )
        enqueueRoomResponse()
        enqueueRoomResponse()
        enqueueRoomResponse()
        enqueueMembersResponse(includeMember = true)

        val harness = harness(MEMBER_ACCESS_TOKEN)
        val metadata = metadata(MEMBER_DEVICE_NAME)

        val resolvedDeviceId = harness.deviceRegistration
            .resolveOrRegister(metadata)
            .getOrThrow()
        assertEquals(MEMBER_DEVICE_ID, resolvedDeviceId)
        assertDeviceRegistrationRequest(
            request = server.takeRequest(),
            token = MEMBER_ACCESS_TOKEN,
            deviceName = MEMBER_DEVICE_NAME
        )

        harness.bootstrap.loadSessionBootstrap(metadata).getOrThrow()
        assertSessionBootstrapRequest(
            request = server.takeRequest(),
            token = MEMBER_ACCESS_TOKEN,
            deviceId = MEMBER_DEVICE_ID,
            deviceName = MEMBER_DEVICE_NAME
        )

        harness.presence.heartbeat().getOrThrow()
        assertHeartbeatRequest(
            request = server.takeRequest(),
            token = MEMBER_ACCESS_TOKEN,
            deviceId = MEMBER_DEVICE_ID
        )

        val joinedRoom = harness.rooms.joinLocalDiscoveryRoom(ROOM_ID).getOrThrow()
        assertEquals(ROOM_ID, joinedRoom.id)
        assertDeviceRoomActionRequest(
            request = server.takeRequest(),
            path = "/api/v1/rooms/$ROOM_ID/join",
            token = MEMBER_ACCESS_TOKEN,
            deviceId = MEMBER_DEVICE_ID
        )

        harness.rooms.activateRoom(ROOM_ID).getOrThrow()
        assertDeviceRoomActionRequest(
            request = server.takeRequest(),
            path = "/api/v1/rooms/$ROOM_ID/activate",
            token = MEMBER_ACCESS_TOKEN,
            deviceId = MEMBER_DEVICE_ID
        )

        harness.rooms.getRoom(ROOM_ID).getOrThrow()
        assertSimpleAuthenticatedRequest(
            request = server.takeRequest(),
            method = "GET",
            path = "/api/v1/rooms/$ROOM_ID",
            token = MEMBER_ACCESS_TOKEN
        )

        val members = harness.rooms.getActiveMembers(ROOM_ID).getOrThrow()
        assertEquals(2, members.size)
        assertEquals(
            setOf(RoomMemberRole.OWNER, RoomMemberRole.MEMBER),
            members.map { it.role }.toSet()
        )
        assertTrue(members.all { it.presenceState == PresenceState.IN_ROOM })
        assertSimpleAuthenticatedRequest(
            request = server.takeRequest(),
            method = "GET",
            path = "/api/v1/rooms/$ROOM_ID/members",
            token = MEMBER_ACCESS_TOKEN
        )

        assertEquals(7, server.requestCount)
    }

    private fun harness(token: String): CompatibilityHarness {
        val deviceStore = FakeDeviceIdStore()
        val retrofit = authenticatedRetrofit(token)

        val deviceRepository = DeviceRepository(
            retrofit.create(DevicesApi::class.java)
        )

        return CompatibilityHarness(
            deviceStore = deviceStore,
            deviceRegistration = DeviceRegistrationCoordinator(
                deviceRepository = deviceRepository,
                deviceIdStore = deviceStore
            ),
            bootstrap = BootstrapRepository(
                bootstrapApi = retrofit.create(BootstrapApi::class.java),
                deviceIdStore = deviceStore
            ),
            presence = PresenceRepository(
                presenceApi = retrofit.create(PresenceApi::class.java),
                deviceIdStore = deviceStore
            ),
            rooms = RoomRepository(
                roomsApi = retrofit.create(RoomsApi::class.java),
                deviceIdStore = deviceStore
            )
        )
    }

    private fun authenticatedRetrofit(token: String): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor { token }
            )
            .build()

        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun metadata(deviceName: String) = SessionBootstrapMetadata(
        displayName = "Compatibility User",
        deviceName = deviceName,
        appVersion = APP_VERSION,
        platform = Platform.ANDROID,
        manufacturer = MANUFACTURER,
        model = deviceName,
        platformVersion = PLATFORM_VERSION,
        locale = LOCALE,
        timezone = TIMEZONE,
        capabilities = listOf("rooms", "presence")
    )

    private fun assertDeviceRegistrationRequest(
        request: RecordedRequest,
        token: String,
        deviceName: String
    ) {
        assertSimpleAuthenticatedRequest(
            request = request,
            method = "POST",
            path = "/api/v1/devices",
            token = token
        )

        val body = request.jsonBody()
        assertEquals(deviceName, body["deviceName"].asString)
        assertEquals("ANDROID", body["platform"].asString)
        assertEquals(APP_VERSION, body["appVersion"].asString)
        assertEquals(MANUFACTURER, body["manufacturer"].asString)
        assertEquals(deviceName, body["model"].asString)
        assertEquals(PLATFORM_VERSION, body["platformVersion"].asString)
        assertEquals(6, body.size())
    }

    private fun assertSessionBootstrapRequest(
        request: RecordedRequest,
        token: String,
        deviceId: String,
        deviceName: String
    ) {
        assertSimpleAuthenticatedRequest(
            request = request,
            method = "POST",
            path = "/api/v1/session/bootstrap",
            token = token
        )

        val body = request.jsonBody()
        assertEquals(deviceId, body["deviceId"].asString)
        assertEquals("Compatibility User", body["displayName"].asString)
        assertEquals(deviceName, body["deviceName"].asString)
        assertEquals(MANUFACTURER, body["manufacturer"].asString)
        assertEquals(deviceName, body["model"].asString)
        assertEquals("ANDROID", body["platform"].asString)
        assertEquals(PLATFORM_VERSION, body["platformVersion"].asString)
        assertEquals(APP_VERSION, body["appVersion"].asString)
        assertEquals(LOCALE, body["locale"].asString)
        assertEquals(TIMEZONE, body["timezone"].asString)
        assertEquals(
            listOf("rooms", "presence"),
            body["capabilities"].asJsonArray.map { it.asString }
        )
        assertEquals(11, body.size())
    }

    private fun assertHeartbeatRequest(
        request: RecordedRequest,
        token: String,
        deviceId: String
    ) {
        assertSimpleAuthenticatedRequest(
            request = request,
            method = "POST",
            path = "/api/v1/presence/heartbeat",
            token = token
        )

        val body = request.jsonBody()
        assertEquals(deviceId, body["deviceId"].asString)
        assertFalse(body.has("state"))
        assertEquals(1, body.size())
    }

    private fun assertOwnerCreateRoomRequest(request: RecordedRequest) {
        assertSimpleAuthenticatedRequest(
            request = request,
            method = "POST",
            path = "/api/v1/rooms",
            token = OWNER_ACCESS_TOKEN
        )

        val body = request.jsonBody()
        assertEquals(OWNER_DEVICE_ID, body["ownerDeviceId"].asString)
        assertEquals(ROOM_NAME, body["name"].asString)
        assertEquals("LOCAL_DISCOVERY", body["visibility"].asString)
        assertEquals(3, body.size())
    }

    private fun assertDeviceRoomActionRequest(
        request: RecordedRequest,
        path: String,
        token: String,
        deviceId: String
    ) {
        assertSimpleAuthenticatedRequest(request, "POST", path, token)
        val body = request.jsonBody()
        assertEquals(deviceId, body["deviceId"].asString)
        assertEquals(1, body.size())
    }

    private fun assertSimpleAuthenticatedRequest(
        request: RecordedRequest,
        method: String,
        path: String,
        token: String
    ) {
        assertEquals(method, request.method)
        assertEquals(path, request.path)
        assertEquals("Bearer $token", request.getHeader("Authorization"))
        assertFalse(request.path.orEmpty().startsWith("/api/v1/bootstrap"))
    }

    private fun RecordedRequest.jsonBody(): JsonObject =
        JsonParser.parseString(body.readUtf8()).asJsonObject

    private fun enqueueDeviceRegistration(
        deviceId: String,
        userId: String,
        deviceName: String
    ) {
        server.enqueue(
            jsonResponse(
                code = 201,
                body = """
                    {
                      "deviceId": "$deviceId",
                      "userId": "$userId",
                      "deviceName": "$deviceName",
                      "platform": "ANDROID",
                      "appVersion": "$APP_VERSION",
                      "lastSeenAt": "$LOCAL_TIME",
                      "createdAt": "$LOCAL_TIME"
                    }
                """.trimIndent()
            )
        )
    }

    private fun enqueueSessionBootstrap(
        userId: String,
        deviceId: String
    ) {
        server.enqueue(
            jsonResponse(
                body = sessionBootstrapJson(
                    userId = userId,
                    deviceId = deviceId
                )
            )
        )
    }

    private fun enqueueHeartbeat(
        userId: String,
        deviceId: String
    ) {
        server.enqueue(
            jsonResponse(
                body = """
                    {
                      "userId": "$userId",
                      "deviceId": "$deviceId",
                      "state": "ONLINE",
                      "currentRoomId": null,
                      "lastSeenAt": "$LOCAL_TIME"
                    }
                """.trimIndent()
            )
        )
    }

    private fun enqueueRoomResponse() {
        server.enqueue(
            jsonResponse(
                body = roomJson()
            )
        )
    }

    private fun enqueueMembersResponse(includeMember: Boolean) {
        val memberJson = if (includeMember) {
            ",\n" + roomMemberJson(
                membershipId = MEMBER_MEMBERSHIP_ID,
                userId = MEMBER_USER_ID,
                username = "member-user",
                displayName = "Member User",
                role = "MEMBER"
            )
        } else {
            ""
        }

        server.enqueue(
            jsonResponse(
                body = """
                    [
                      ${roomMemberJson(
                          membershipId = OWNER_MEMBERSHIP_ID,
                          userId = OWNER_USER_ID,
                          username = "owner-user",
                          displayName = "Owner User",
                          role = "OWNER"
                      )}$memberJson
                    ]
                """.trimIndent()
            )
        )
    }

    private fun sessionBootstrapJson(
        userId: String,
        deviceId: String
    ): String = """
        {
          "session": {
            "authenticated": true,
            "guestAllowed": false,
            "needsLogin": false,
            "accessToken": "access-token",
            "refreshToken": "refresh-token",
            "refreshExpiresAt": "$OFFSET_TIME"
          },
          "user": {
            "id": "$userId",
            "email": null,
            "phone": null,
            "username": "compat-user",
            "displayName": "Compatibility User",
            "avatarURL": null,
            "emailVerified": false,
            "phoneVerified": false,
            "settings": {
              "theme": "SYSTEM",
              "allowDiscovery": true,
              "showLastSeen": true,
              "notificationsEnabled": false,
              "preferredAudioQuality": "BALANCED_LATENCY"
            }
          },
          "configuration": {
            "heartbeatIntervalSeconds": 5,
            "presenceTimeoutSeconds": 90,
            "maxRoomMembers": 5,
            "maxRoomNameLength": 80,
            "udpPort": 8090,
            "audioCodec": "DEFAULT",
            "audioSampleRate": 44100,
            "audioFrameDurationMs": 24,
            "maxAudioPacketSize": 4096,
            "websocketEndpoint": "/ws/rooms/{roomId}/audio",
            "apiVersion": "v1",
            "supportsGuestLogin": false
          },
          "capabilities": {
            "audio": true,
            "rooms": true,
            "presence": true,
            "invites": true,
            "websocket": true,
            "pushNotifications": false,
            "guestAuthentication": false,
            "multiDevice": true,
            "moderation": false,
            "deviceManagement": true,
            "fileTransfer": false,
            "screenShare": false
          },
          "devices": [
            {
              "id": "$deviceId",
              "deviceName": "compat-device",
              "platform": "ANDROID",
              "appVersion": "$APP_VERSION",
              "lastSeenAt": "$LOCAL_TIME"
            }
          ],
          "rooms": [],
          "presence": {
            "userId": "$userId",
            "state": "ONLINE",
            "currentRoomId": null,
            "lastSeenAt": "$LOCAL_TIME",
            "updatedAt": "$LOCAL_TIME"
          },
          "server": {
            "name": "ServeRelay",
            "version": "1.0.0",
            "build": "dev",
            "environment": "local",
            "apiVersion": "v1",
            "serverTime": "$OFFSET_TIME",
            "maintenanceMode": false
          }
        }
    """.trimIndent()

    private fun roomJson(): String = """
        {
          "id": "$ROOM_ID",
          "ownerUserId": "$OWNER_USER_ID",
          "ownerDeviceId": "$OWNER_DEVICE_ID",
          "name": "$ROOM_NAME",
          "status": "ACTIVE",
          "visibility": "LOCAL_DISCOVERY",
          "createdAt": "$LOCAL_TIME",
          "updatedAt": "$LOCAL_TIME",
          "archivedAt": null
        }
    """.trimIndent()

    private fun roomMemberJson(
        membershipId: String,
        userId: String,
        username: String,
        displayName: String,
        role: String
    ): String = """
        {
          "id": "$membershipId",
          "roomId": "$ROOM_ID",
          "userId": "$userId",
          "username": "$username",
          "displayName": "$displayName",
          "avatarURL": null,
          "role": "$role",
          "state": "ACTIVE",
          "presenceState": "IN_ROOM",
          "presenceLastSeenAt": "$LOCAL_TIME",
          "joinedAt": "$LOCAL_TIME",
          "membershipLastSeenAt": "$LOCAL_TIME",
          "leftAt": null
        }
    """.trimIndent()

    private fun jsonResponse(
        code: Int = 200,
        body: String
    ): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private data class CompatibilityHarness(
        val deviceStore: FakeDeviceIdStore,
        val deviceRegistration: DeviceRegistrationCoordinator,
        val bootstrap: BootstrapRepository,
        val presence: PresenceRepository,
        val rooms: RoomRepository
    )

    private companion object {
        const val OWNER_ACCESS_TOKEN = "owner-access-token"
        const val MEMBER_ACCESS_TOKEN = "member-access-token"

        const val OWNER_USER_ID = "11111111-1111-1111-1111-111111111111"
        const val MEMBER_USER_ID = "22222222-2222-2222-2222-222222222222"
        const val OWNER_DEVICE_ID = "33333333-3333-3333-3333-333333333333"
        const val MEMBER_DEVICE_ID = "44444444-4444-4444-4444-444444444444"
        const val ROOM_ID = "55555555-5555-5555-5555-555555555555"
        const val OWNER_MEMBERSHIP_ID = "66666666-6666-6666-6666-666666666666"
        const val MEMBER_MEMBERSHIP_ID = "77777777-7777-7777-7777-777777777777"

        const val OWNER_DEVICE_NAME = "Pixel 10 Pro"
        const val MEMBER_DEVICE_NAME = "Galaxy S26 Ultra"
        const val ROOM_NAME = "Compatibility Room"
        const val MANUFACTURER = "Compatibility Manufacturer"
        const val APP_VERSION = "1.0.0"
        const val PLATFORM_VERSION = "17"
        const val LOCALE = "en-US"
        const val TIMEZONE = "Europe/Vilnius"
        const val LOCAL_TIME = "2026-08-24T16:00:00"
        const val OFFSET_TIME = "2026-08-24T16:00:00Z"
    }
}
