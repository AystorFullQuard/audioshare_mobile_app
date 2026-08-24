package mme.corp.audioshare.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.api.BootstrapApi
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.testutil.FakeDeviceIdStore
import mme.corp.audioshare.testutil.retrofit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BootstrapRepositoryTest {
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
    fun sendsStoredDeviceIdAndPersistsResolvedDeviceId() = runTest {
        server.enqueue(successResponse())
        val store = FakeDeviceIdStore("stored-device-id")
        val repository = repository(store)

        val result = repository.bootstrap(
            displayName = "Test User",
            deviceName = "Pixel 8",
            appVersion = "1.0"
        )

        assertTrue(result.isSuccess)
        assertEquals(PresenceState.ONLINE, result.getOrThrow().presenceState)
        assertEquals("resolved-device-id", store.storedDeviceId)

        val body = JsonParser.parseString(
            server.takeRequest().body.readUtf8()
        ).asJsonObject

        assertEquals("stored-device-id", body["deviceId"].asString)
        assertEquals("Test User", body["displayName"].asString)
        assertEquals("Pixel 8", body["deviceName"].asString)
        assertEquals("ANDROID", body["platform"].asString)
        assertEquals("1.0", body["appVersion"].asString)
    }

    @Test
    fun firstBootstrapDoesNotSendNullDeviceId() = runTest {
        server.enqueue(successResponse())
        val store = FakeDeviceIdStore()
        val repository = repository(store)

        val result = repository.bootstrap(
            displayName = null,
            deviceName = "Pixel 8",
            appVersion = null
        )

        assertTrue(result.isSuccess)

        val body = JsonParser.parseString(
            server.takeRequest().body.readUtf8()
        ).asJsonObject

        assertFalse(body.has("deviceId"))
    }

    @Test
    fun sessionBootstrapMatchesCurrentServeRelayContract() = runTest {
        server.enqueue(sessionBootstrapSuccessResponse())
        val repository = repository(FakeDeviceIdStore(DEVICE_ID))

        val result = repository.loadSessionBootstrap(
            SessionBootstrapMetadata(
                displayName = "Test User",
                deviceName = "Pixel 10 Pro",
                appVersion = "1.0.0",
                platform = Platform.ANDROID,
                manufacturer = "Google",
                model = "Pixel 10 Pro",
                platformVersion = "17",
                locale = "en-US",
                timezone = "America/Los_Angeles",
                capabilities = listOf("rooms", "presence")
            )
        )

        assertTrue(result.isSuccess)
        val bootstrap = result.getOrThrow()
        assertEquals(ROOM_ID, bootstrap.rooms.single().id)
        assertNull(bootstrap.rooms.single().name)
        assertEquals(ROOM_ID, bootstrap.presence?.currentRoomId)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/session/bootstrap", request.path)

        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(DEVICE_ID, body["deviceId"].asString)
        assertEquals("Test User", body["displayName"].asString)
        assertEquals("Pixel 10 Pro", body["deviceName"].asString)
        assertEquals("Google", body["manufacturer"].asString)
        assertEquals("Pixel 10 Pro", body["model"].asString)
        assertEquals("ANDROID", body["platform"].asString)
        assertEquals("17", body["platformVersion"].asString)
        assertEquals("1.0.0", body["appVersion"].asString)
        assertEquals("en-US", body["locale"].asString)
        assertEquals("America/Los_Angeles", body["timezone"].asString)
        assertEquals(
            listOf("rooms", "presence"),
            body["capabilities"].asJsonArray.map { it.asString }
        )
    }

    @Test
    fun sessionBootstrapWithoutPersistedDeviceFailsBeforeNetworkCall() = runTest {
        val repository = repository(FakeDeviceIdStore())

        val result = repository.loadSessionBootstrap(
            SessionBootstrapMetadata()
        )

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    private fun repository(store: FakeDeviceIdStore): BootstrapRepository {
        val api = server.retrofit().create(BootstrapApi::class.java)
        return BootstrapRepository(api, store)
    }

    private fun successResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
              "userId": "user-id",
              "deviceId": "resolved-device-id",
              "displayName": "Test User",
              "presenceState": "ONLINE"
            }
            """.trimIndent()
        )

    private fun sessionBootstrapSuccessResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
              "session": {
                "authenticated": true,
                "guestAllowed": false,
                "needsLogin": false,
                "accessToken": "access-token",
                "refreshToken": "refresh-token",
                "refreshExpiresAt": "2026-09-24T12:00:00Z"
              },
              "user": {
                "id": "$USER_ID",
                "email": "test@example.com",
                "phone": null,
                "username": "test-user",
                "displayName": "Test User",
                "avatarURL": "/default.jpg",
                "emailVerified": true,
                "phoneVerified": false,
                "settings": {
                  "theme": "SYSTEM",
                  "allowDiscovery": true,
                  "showLastSeen": true,
                  "notificationsEnabled": true,
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
                  "id": "$DEVICE_ID",
                  "deviceName": "Pixel 10 Pro",
                  "platform": "ANDROID",
                  "appVersion": "1.0.0",
                  "lastSeenAt": "2026-08-24T12:00:00"
                }
              ],
              "rooms": [
                {
                  "id": "$ROOM_ID",
                  "ownerUserId": "$OWNER_USER_ID",
                  "ownerDeviceId": "$OWNER_DEVICE_ID",
                  "name": null,
                  "status": "ACTIVE",
                  "visibility": "LOCAL_DISCOVERY",
                  "currentUserRole": "MEMBER",
                  "activeMemberCount": 2,
                  "createdAt": "2026-08-24T12:00:00",
                  "updatedAt": "2026-08-24T12:00:00",
                  "archivedAt": null
                }
              ],
              "presence": {
                "userId": "$USER_ID",
                "state": "IN_ROOM",
                "currentRoomId": "$ROOM_ID",
                "lastSeenAt": "2026-08-24T12:00:00",
                "updatedAt": "2026-08-24T12:00:00"
              },
              "server": {
                "name": "ServeRelay",
                "version": "1.0.0",
                "build": "dev",
                "environment": "local",
                "apiVersion": "v1",
                "serverTime": "2026-08-24T12:00:00Z",
                "maintenanceMode": false
              }
            }
            """.trimIndent()
        )

    private companion object {
        const val DEVICE_ID = "11111111-1111-1111-1111-111111111111"
        const val USER_ID = "22222222-2222-2222-2222-222222222222"
        const val ROOM_ID = "33333333-3333-3333-3333-333333333333"
        const val OWNER_USER_ID = "44444444-4444-4444-4444-444444444444"
        const val OWNER_DEVICE_ID = "55555555-5555-5555-5555-555555555555"
    }
}
