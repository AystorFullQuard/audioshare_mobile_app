package mme.corp.audioshare.startup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.data.api.DevicesApi
import mme.corp.audioshare.data.dto.bootstrap.Platform
import mme.corp.audioshare.data.dto.device.DeviceResponse
import mme.corp.audioshare.data.dto.device.RegisterDeviceRequest
import mme.corp.audioshare.data.repository.DeviceRepository
import mme.corp.audioshare.data.repository.SessionBootstrapMetadata
import mme.corp.audioshare.testutil.FakeDeviceIdStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class DeviceRegistrationCoordinatorTest {

    @Test
    fun existingDeviceIsReusedWithoutRegistration() = runTest {
        val api = RecordingDevicesApi()
        val store = FakeDeviceIdStore(DEVICE_ID)
        val coordinator = coordinator(api, store)

        val result = coordinator.resolveOrRegister(
            SessionBootstrapMetadata()
        )

        assertEquals(DEVICE_ID, result.getOrThrow())
        assertEquals(0, api.requestCount)
        assertEquals(DEVICE_ID, store.storedDeviceId)
    }

    @Test
    fun missingDeviceIsRegisteredAndPersistedBeforeStartupContinues() = runTest {
        val api = RecordingDevicesApi()
        val store = FakeDeviceIdStore()
        val coordinator = coordinator(api, store)

        val result = coordinator.resolveOrRegister(metadata())

        assertEquals(DEVICE_ID, result.getOrThrow())
        assertEquals(1, api.requestCount)
        assertEquals(DEVICE_ID, store.storedDeviceId)
        assertEquals(
            RegisterDeviceRequest(
                deviceName = "Pixel 10 Pro",
                platform = Platform.ANDROID,
                appVersion = "1.0.0",
                manufacturer = "Google",
                model = "Pixel 10 Pro",
                platformVersion = "17"
            ),
            api.lastRequest
        )
    }

    @Test
    fun missingRequiredRegistrationMetadataFailsWithoutNetworkCall() = runTest {
        val api = RecordingDevicesApi()
        val store = FakeDeviceIdStore()
        val coordinator = coordinator(api, store)

        val result = coordinator.resolveOrRegister(
            SessionBootstrapMetadata(
                deviceName = null,
                manufacturer = "Google",
                model = "Pixel 10 Pro"
            )
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, api.requestCount)
        assertNull(store.storedDeviceId)
    }

    @Test
    fun registrationFailureDoesNotPersistDeviceIdentity() = runTest {
        val expected = IllegalStateException("registration failed")
        val api = RecordingDevicesApi(failure = expected)
        val store = FakeDeviceIdStore()
        val coordinator = coordinator(api, store)

        val result = coordinator.resolveOrRegister(metadata())

        assertTrue(result.isFailure)
        assertSame(expected, result.exceptionOrNull())
        assertEquals(1, api.requestCount)
        assertNull(store.storedDeviceId)
    }

    @Test
    fun registrationCancellationIsPropagated() = runTest {
        val expected = CancellationException("cancelled")
        val api = RecordingDevicesApi(failure = expected)
        val coordinator = coordinator(api, FakeDeviceIdStore())

        try {
            coordinator.resolveOrRegister(metadata())
            fail("CancellationException must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }

    private fun coordinator(
        api: RecordingDevicesApi,
        store: FakeDeviceIdStore
    ) = DeviceRegistrationCoordinator(
        deviceRepository = DeviceRepository(api),
        deviceIdStore = store
    )

    private class RecordingDevicesApi(
        private val failure: Exception? = null
    ) : DevicesApi {
        var requestCount = 0
        var lastRequest: RegisterDeviceRequest? = null

        override suspend fun registerDevice(
            request: RegisterDeviceRequest
        ): Response<DeviceResponse> {
            requestCount += 1
            lastRequest = request
            failure?.let { throw it }
            return Response.success(deviceResponse())
        }
    }

    private companion object {
        const val DEVICE_ID = "11111111-1111-1111-1111-111111111111"

        fun metadata() = SessionBootstrapMetadata(
            deviceName = "Pixel 10 Pro",
            manufacturer = "Google",
            model = "Pixel 10 Pro",
            platform = Platform.ANDROID,
            appVersion = "1.0.0",
            platformVersion = "17"
        )

        fun deviceResponse() = DeviceResponse(
            deviceId = DEVICE_ID,
            userId = "22222222-2222-2222-2222-222222222222",
            deviceName = "Pixel 10 Pro",
            platform = Platform.ANDROID,
            appVersion = "1.0.0",
            lastSeenAt = "2026-08-24T12:00:00",
            createdAt = "2026-08-24T12:00:00"
        )
    }
}
