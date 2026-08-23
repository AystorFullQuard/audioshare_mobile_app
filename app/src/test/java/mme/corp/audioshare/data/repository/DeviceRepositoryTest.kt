package mme.corp.audioshare.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.api.DevicesApi
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.testutil.retrofit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DeviceRepositoryTest {

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
    fun registerDeviceMatchesServeRelayContract() = runTest {
        server.enqueue(successResponse())
        val repository = repository()

        val result = repository.registerDevice(
            deviceName = " Pixel 10 Pro ",
            manufacturer = " Google ",
            model = " Pixel 10 Pro ",
            platform = Platform.ANDROID,
            appVersion = " 1.0.0 ",
            platformVersion = " 17 "
        )

        assertTrue(result.isSuccess)
        val device = result.getOrThrow()
        assertEquals("device-id", device.deviceId)
        assertEquals("user-id", device.userId)
        assertEquals("Pixel 10 Pro", device.deviceName)
        assertEquals(Platform.ANDROID, device.platform)
        assertEquals("1.0.0", device.appVersion)
        assertEquals("2026-08-24T00:00:00", device.lastSeenAt)
        assertEquals("2026-08-24T00:00:00", device.createdAt)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/devices", request.path)

        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("Pixel 10 Pro", body["deviceName"].asString)
        assertEquals("ANDROID", body["platform"].asString)
        assertEquals("1.0.0", body["appVersion"].asString)
        assertEquals("Google", body["manufacturer"].asString)
        assertEquals("Pixel 10 Pro", body["model"].asString)
        assertEquals("17", body["platformVersion"].asString)
    }

    @Test
    fun optionalMetadataIsOmittedWhenBlank() = runTest {
        server.enqueue(successResponse())
        val repository = repository()

        val result = repository.registerDevice(
            deviceName = "Pixel 10 Pro",
            manufacturer = "Google",
            model = "Pixel 10 Pro",
            appVersion = "   ",
            platformVersion = null
        )

        assertTrue(result.isSuccess)

        val body = JsonParser.parseString(
            server.takeRequest().body.readUtf8()
        ).asJsonObject

        assertFalse(body.has("appVersion"))
        assertFalse(body.has("platformVersion"))
    }

    @Test
    fun invalidRequiredMetadataFailsBeforeNetworkCall() = runTest {
        val repository = repository()

        val blankDeviceName = repository.registerDevice(
            deviceName = "   ",
            manufacturer = "Google",
            model = "Pixel 10 Pro"
        )
        val blankManufacturer = repository.registerDevice(
            deviceName = "Pixel 10 Pro",
            manufacturer = "   ",
            model = "Pixel 10 Pro"
        )
        val blankModel = repository.registerDevice(
            deviceName = "Pixel 10 Pro",
            manufacturer = "Google",
            model = "   "
        )

        assertTrue(blankDeviceName.exceptionOrNull() is IllegalArgumentException)
        assertTrue(blankManufacturer.exceptionOrNull() is IllegalArgumentException)
        assertTrue(blankModel.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mapsServeRelayDeviceLimitError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "code": "DEVICE_LIMIT_REACHED",
                      "message": "Device limit reached",
                      "fieldErrors": {},
                      "timestamp": "2026-08-24T00:00:00"
                    }
                    """.trimIndent()
                )
        )
        val repository = repository()

        val result = repository.registerDevice(
            deviceName = "Pixel 10 Pro",
            manufacturer = "Google",
            model = "Pixel 10 Pro"
        )

        val exception = result.exceptionOrNull() as ApiException
        assertEquals(409, exception.httpCode)
        assertEquals("DEVICE_LIMIT_REACHED", exception.apiError.code)
    }

    @Test
    fun successfulResponseWithoutBodyIsRejected() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
        )
        val repository = repository()

        val result = repository.registerDevice(
            deviceName = "Pixel 10 Pro",
            manufacturer = "Google",
            model = "Pixel 10 Pro"
        )

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun blankIdentityInSuccessfulResponseIsRejected() = runTest {
        server.enqueue(successResponse(deviceId = ""))
        val repository = repository()

        val result = repository.registerDevice(
            deviceName = "Pixel 10 Pro",
            manufacturer = "Google",
            model = "Pixel 10 Pro"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun cancellationFromApiIsRethrown() = runTest {
        val expected = CancellationException("cancelled")
        val api = DevicesApi { throw expected }
        val repository = DeviceRepository(api)

        try {
            repository.registerDevice(
                deviceName = "Pixel 10 Pro",
                manufacturer = "Google",
                model = "Pixel 10 Pro"
            )
            fail("CancellationException must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }

    private fun repository(): DeviceRepository {
        val api = server.retrofit().create(DevicesApi::class.java)
        return DeviceRepository(api)
    }

    private fun successResponse(
        deviceId: String = "device-id"
    ): MockResponse = MockResponse()
        .setResponseCode(201)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
              "deviceId": "$deviceId",
              "userId": "user-id",
              "deviceName": "Pixel 10 Pro",
              "platform": "ANDROID",
              "appVersion": "1.0.0",
              "lastSeenAt": "2026-08-24T00:00:00",
              "createdAt": "2026-08-24T00:00:00"
            }
            """.trimIndent()
        )
}
