package mme.corp.audioshare.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.api.PresenceApi
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.exception.DeviceBootstrapRequiredException
import mme.corp.audioshare.testutil.FakeDeviceIdStore
import mme.corp.audioshare.testutil.retrofit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PresenceRepositoryTest {
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
    fun heartbeatWithoutStatePreservesServerStateContract() = runTest {
        server.enqueue(successResponse("IN_ROOM", "room-id"))
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.heartbeat()

        assertTrue(result.isSuccess)
        assertEquals(PresenceState.IN_ROOM, result.getOrThrow().state)
        assertEquals("room-id", result.getOrThrow().currentRoomId)
        assertEquals("AUTO", result.getOrThrow().preference.name)

        val body = JsonParser.parseString(
            server.takeRequest().body.readUtf8()
        ).asJsonObject

        assertEquals("device-id", body["deviceId"].asString)
        assertFalse(body.has("state"))
    }

    @Test
    fun heartbeatDoesNotPermitClientToOverrideServerActivity() = runTest {
        server.enqueue(successResponse("STREAMING", "room-id"))
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.heartbeat(PresenceState.STREAMING)

        assertTrue(result.isSuccess)
        assertEquals(PresenceState.STREAMING, result.getOrThrow().state)

        val body = JsonParser.parseString(
            server.takeRequest().body.readUtf8()
        ).asJsonObject

        assertFalse(body.has("state"))
    }

    @Test
    fun heartbeatWithoutDeviceIdFailsBeforeNetworkCall() = runTest {
        val repository = repository(FakeDeviceIdStore())

        val result = repository.heartbeat()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeviceBootstrapRequiredException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mapsServeRelayApiError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "code": "DEVICE_NOT_OWNED",
                      "message": "Heartbeat device does not match authenticated device",
                      "fieldErrors": {},
                      "timestamp": "2026-07-28T12:00:00"
                    }
                    """.trimIndent()
                )
        )
        val repository = repository(FakeDeviceIdStore("device-id"))

        val result = repository.heartbeat(PresenceState.STREAMING)

        val exception = result.exceptionOrNull() as ApiException
        assertEquals(403, exception.httpCode)
        assertEquals("DEVICE_NOT_OWNED", exception.apiError.code)
    }

    private fun repository(store: FakeDeviceIdStore): PresenceRepository {
        val api = server.retrofit().create(PresenceApi::class.java)
        return PresenceRepository(api, store)
    }

    private fun successResponse(state: String, currentRoomId: String?): MockResponse {
        val roomField = currentRoomId?.let { "\"$it\"" } ?: "null"

        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "userId": "user-id",
                  "deviceId": "device-id",
                  "availability": "ONLINE",
                  "activity": "$state",
                  "preference": "AUTO",
                  "currentRoomId": $roomField,
                  "lastSeenAt": "2026-07-28T12:00:00"
                }
                """.trimIndent()
            )
    }
}
