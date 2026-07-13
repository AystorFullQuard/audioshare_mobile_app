package mme.corp.audioshare.backend

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.backend.storage.BackendSession
import mme.corp.audioshare.backend.storage.SessionStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServeRelayHttpClientTest {
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
    fun authenticatedRequestAddsBearerToken() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(ME_RESPONSE)
        )

        val client = ServeRelayHttpClient.create(
            config = ServeRelayConfig(baseUrl = server.url("/").toString()),
            sessionStore = FakeSessionStore(
                BackendSession(accessToken = "access-token")
            )
        )

        val response = client.authApi.me()
        val request = server.takeRequest(1, TimeUnit.SECONDS)

        assertTrue(response.isSuccessful)
        assertEquals("Bearer access-token", request?.getHeader("Authorization"))
    }

    private class FakeSessionStore(
        initialSession: BackendSession
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
        const val ME_RESPONSE = """
            {
              "id": "00000000-0000-0000-0000-000000000001",
              "email": null,
              "phone": null,
              "username": "dev-user",
              "displayName": "Dev User",
              "avatarURL": null,
              "emailVerified": false,
              "phoneVerified": false,
              "settings": {
                "theme": "SYSTEM",
                "allowDiscovery": true,
                "showLastSeen": true,
                "notificationsEnabled": true,
                "preferredAudioQuality": "BALANCED_LATENCY"
              }
            }
        """
    }
}
