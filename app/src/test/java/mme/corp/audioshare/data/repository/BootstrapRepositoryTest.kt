package mme.corp.audioshare.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.api.BootstrapApi
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
    fun sessionBootstrapUsesPersistedDeviceAndMapsRoomSnapshot() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "rooms": [
                        {
                          "id": "room-1",
                          "ownerUserId": "owner-1",
                          "ownerDeviceId": "device-1",
                          "name": null,
                          "status": "ACTIVE",
                          "visibility": "LOCAL_DISCOVERY",
                          "currentUserRole": "MEMBER",
                          "activeMemberCount": 2,
                          "createdAt": "2026-07-31T12:00:00",
                          "updatedAt": "2026-07-31T12:00:00",
                          "archivedAt": null
                        }
                      ],
                      "presence": {
                        "userId": "user-id",
                        "state": "IN_ROOM",
                        "currentRoomId": "room-1",
                        "lastSeenAt": "2026-07-31T12:00:00",
                        "updatedAt": "2026-07-31T12:00:00"
                      }
                    }
                    """.trimIndent()
                )
        )
        val repository = repository(FakeDeviceIdStore("stored-device-id"))

        val result = repository.loadSessionBootstrap(
            displayName = "Test User",
            deviceName = "Pixel 8",
            appVersion = "1.0"
        )

        assertTrue(result.isSuccess)
        assertEquals("room-1", result.getOrThrow().rooms.single().id)
        assertNull(result.getOrThrow().rooms.single().name)
        assertEquals("room-1", result.getOrThrow().presence?.currentRoomId)

        val request = server.takeRequest()
        assertEquals("/api/v1/session/bootstrap", request.path)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("stored-device-id", body["deviceId"].asString)
    }

    @Test
    fun sessionBootstrapWithoutPersistedDeviceFailsBeforeNetworkCall() = runTest {
        val repository = repository(FakeDeviceIdStore())

        val result = repository.loadSessionBootstrap(
            displayName = null,
            deviceName = null,
            appVersion = null
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
}
