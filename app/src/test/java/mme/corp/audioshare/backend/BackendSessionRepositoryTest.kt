package mme.corp.audioshare.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.backend.storage.BackendSession
import mme.corp.audioshare.backend.storage.SessionStore
import mme.corp.audioshare.session.BackendSessionRepository
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackendSessionRepositoryTest {
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
    fun devRegisterPersistsAuthenticationSession() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(LOGIN_RESPONSE)
        )

        val store = FakeSessionStore()
        val client = ServeRelayHttpClient.create(
            config = ServeRelayConfig(baseUrl = server.url("/").toString()),
            sessionStore = store
        )
        val repository = BackendSessionRepository(
            authApi = client.authApi,
            bootstrapApi = client.bootstrapApi,
            presenceApi = client.presenceApi,
            devicesApi = client.devicesApi,
            sessionStore = store,
            json = client.json
        )

        val result = repository.devRegister()

        assertTrue(result is ApiResult.Success)
        assertEquals("user-id", store.session.value.userId)
        assertEquals("session-id", store.session.value.sessionId)
        assertEquals("access-token", store.session.value.accessToken)
        assertEquals("refresh-token", store.session.value.refreshToken)
    }

    private class FakeSessionStore : SessionStore {
        private val mutableSession = MutableStateFlow(BackendSession())
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
        const val LOGIN_RESPONSE = """
            {
              "userId": "user-id",
              "sessionId": "session-id",
              "tokenType": "Bearer",
              "accessToken": "access-token",
              "refreshToken": "refresh-token"
            }
        """
    }
}
