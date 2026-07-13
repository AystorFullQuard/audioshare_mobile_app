package mme.corp.audioshare.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.backend.storage.BackendSession
import mme.corp.audioshare.backend.storage.SessionStore
import mme.corp.audioshare.session.BackendSessionRepository
import mme.corp.audioshare.session.BackendSessionStartupRequest
import mme.corp.audioshare.session.BackendSessionStartupResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackendSessionStartupTest {
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
    fun freshInstallRegistersBootstrapsAndLoadsBackendSession() = runTest {
        enqueueJson(LOGIN_RESPONSE)
        enqueueJson(ME_RESPONSE)
        enqueueJson(BOOTSTRAP_RESPONSE)
        enqueueJson(HEARTBEAT_RESPONSE)
        enqueueJson(DEVICES_RESPONSE)

        val store = FakeSessionStore()
        val repository = createRepository(store)

        val result = repository.initializeSession(STARTUP_REQUEST)

        assertTrue(result is BackendSessionStartupResult.Success)
        val snapshot = (result as BackendSessionStartupResult.Success).snapshot
        assertEquals(USER_ID, snapshot.userId)
        assertEquals(DEVICE_ID, snapshot.deviceId)
        assertEquals("Google Pixel 10 Pro", snapshot.deviceName)
        assertEquals(1, snapshot.deviceCount)
        assertEquals(USER_ID, store.session.value.userId)
        assertEquals(DEVICE_ID, store.session.value.deviceId)

        val register = server.takeRequest()
        assertEquals("/api/v1/auth/dev-register", register.path)
        assertEquals(null, register.getHeader("Authorization"))

        val me = server.takeRequest()
        assertEquals("/api/v1/auth/me", me.path)
        assertEquals("Bearer access-token", me.getHeader("Authorization"))

        assertEquals("/api/v1/bootstrap", server.takeRequest().path)
        assertEquals("/api/v1/presence/heartbeat", server.takeRequest().path)
        assertEquals("/api/v1/devices", server.takeRequest().path)
    }

    @Test
    fun existingSessionIsReusedWithoutDevRegistration() = runTest {
        enqueueJson(ME_RESPONSE)
        enqueueJson(BOOTSTRAP_RESPONSE)
        enqueueJson(HEARTBEAT_RESPONSE)
        enqueueJson(DEVICES_RESPONSE)

        val store = FakeSessionStore(
            BackendSession(
                userId = USER_ID,
                sessionId = "stored-session",
                accessToken = "stored-token",
                refreshToken = "stored-refresh",
                deviceId = DEVICE_ID
            )
        )
        val repository = createRepository(store)

        val result = repository.initializeSession(STARTUP_REQUEST)

        assertTrue(result is BackendSessionStartupResult.Success)
        val firstRequest = server.takeRequest()
        assertEquals("/api/v1/auth/me", firstRequest.path)
        assertEquals("Bearer stored-token", firstRequest.getHeader("Authorization"))
        assertFalse(server.requestCount > 4)
    }

    @Test
    fun expiredAccessTokenUsesDevLoginAndRetriesCurrentUser() = runTest {
        enqueueJson(
            body = """{"code":"UNAUTHORIZED","message":"expired"}""",
            responseCode = 401
        )
        enqueueJson(LOGIN_RESPONSE)
        enqueueJson(ME_RESPONSE)
        enqueueJson(BOOTSTRAP_RESPONSE)
        enqueueJson(HEARTBEAT_RESPONSE)
        enqueueJson(DEVICES_RESPONSE)

        val store = FakeSessionStore(
            BackendSession(
                userId = USER_ID,
                sessionId = "old-session",
                accessToken = "expired-token",
                refreshToken = "old-refresh",
                deviceId = DEVICE_ID
            )
        )
        val repository = createRepository(store)

        val result = repository.initializeSession(STARTUP_REQUEST)

        assertTrue(result is BackendSessionStartupResult.Success)

        val firstMe = server.takeRequest()
        assertEquals("/api/v1/auth/me", firstMe.path)
        assertEquals("Bearer expired-token", firstMe.getHeader("Authorization"))

        val login = server.takeRequest()
        assertEquals("/api/v1/auth/dev-login", login.path)
        assertEquals(null, login.getHeader("Authorization"))

        val retriedMe = server.takeRequest()
        assertEquals("/api/v1/auth/me", retriedMe.path)
        assertEquals("Bearer access-token", retriedMe.getHeader("Authorization"))
    }

    private fun createRepository(store: SessionStore): BackendSessionRepository {
        val client = ServeRelayHttpClient.create(
            config = ServeRelayConfig(baseUrl = server.url("/").toString()),
            sessionStore = store
        )
        return BackendSessionRepository(
            authApi = client.authApi,
            bootstrapApi = client.bootstrapApi,
            presenceApi = client.presenceApi,
            devicesApi = client.devicesApi,
            sessionStore = store,
            json = client.json
        )
    }

    private fun enqueueJson(body: String, responseCode: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(responseCode)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private class FakeSessionStore(
        initialSession: BackendSession = BackendSession()
    ) : SessionStore {
        private val mutableSession = MutableStateFlow(initialSession)
        override val session: StateFlow<BackendSession> = mutableSession

        override suspend fun load(): BackendSession = mutableSession.value

        override fun currentAccessToken(): String? = mutableSession.value.accessToken

        override suspend fun saveAuthentication(
            userId: String,
            sessionId: String,
            accessToken: String,
            refreshToken: String
        ) {
            mutableSession.value = mutableSession.value.copy(
                userId = userId,
                sessionId = sessionId,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        }

        override suspend fun saveDeviceId(deviceId: String) {
            mutableSession.value = mutableSession.value.copy(deviceId = deviceId)
        }

        override suspend fun clear() {
            mutableSession.value = BackendSession()
        }
    }

    private companion object {
        const val USER_ID = "11111111-1111-1111-1111-111111111111"
        const val DEVICE_ID = "22222222-2222-2222-2222-222222222222"

        val STARTUP_REQUEST = BackendSessionStartupRequest(
            displayName = "Pixel 10 Pro",
            deviceName = "Google Pixel 10 Pro",
            appVersion = "1.0"
        )

        const val LOGIN_RESPONSE = """
            {
              "userId": "$USER_ID",
              "sessionId": "session-id",
              "tokenType": "Bearer",
              "accessToken": "access-token",
              "refreshToken": "refresh-token"
            }
        """

        const val ME_RESPONSE = """
            {
              "id": "$USER_ID",
              "email": null,
              "phone": null,
              "username": null,
              "displayName": "Pixel 10 Pro",
              "avatarURL": null,
              "emailVerified": false,
              "phoneVerified": false,
              "settings": null
            }
        """

        const val BOOTSTRAP_RESPONSE = """
            {
              "userId": "$USER_ID",
              "deviceId": "$DEVICE_ID",
              "displayName": "Pixel 10 Pro",
              "presenceState": "ONLINE"
            }
        """

        const val HEARTBEAT_RESPONSE = """
            {
              "userId": "$USER_ID",
              "deviceId": "$DEVICE_ID",
              "state": "ONLINE",
              "currentRoomId": null,
              "lastSeenAt": "2026-07-13T12:00:00"
            }
        """

        const val DEVICES_RESPONSE = """
            [
              {
                "id": "$DEVICE_ID",
                "deviceName": "Google Pixel 10 Pro",
                "platform": "ANDROID",
                "appVersion": "1.0",
                "lastSeenAt": "2026-07-13T12:00:00",
                "createdAt": "2026-07-13T11:00:00"
              }
            ]
        """
    }
}
