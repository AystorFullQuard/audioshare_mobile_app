package mme.corp.audioshare.network.retrofit

import mme.corp.audioshare.data.storage.TokenSnapshot
import mme.corp.audioshare.data.storage.TokenStore
import mme.corp.audioshare.network.interceptor.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthRecoveryIntegrationTest {

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
    fun unauthorizedProtectedRequestRefreshesAndRetriesWithNewToken() {
        val store = FakeTokenStore(oldSnapshot())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path) {
                    REFRESH_PATH -> successfulRefreshResponse()
                    PROTECTED_PATH -> {
                        if (
                            request.getHeader(AUTHORIZATION) ==
                            "Bearer $NEW_ACCESS_TOKEN"
                        ) {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody("ok")
                        } else {
                            MockResponse().setResponseCode(401)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
        }

        val response = authenticatedClient(store)
            .newCall(protectedRequest())
            .execute()

        response.use {
            assertEquals(200, it.code)
        }

        assertEquals(newSnapshot(), store.snapshot())
        assertEquals(0, store.clearCalls)

        val first = server.takeRequest()
        val refresh = server.takeRequest()
        val retry = server.takeRequest()

        assertEquals(PROTECTED_PATH, first.path)
        assertEquals("Bearer $OLD_ACCESS_TOKEN", first.getHeader(AUTHORIZATION))
        assertEquals(REFRESH_PATH, refresh.path)
        assertNull(refresh.getHeader(AUTHORIZATION))
        assertEquals(PROTECTED_PATH, retry.path)
        assertEquals("Bearer $NEW_ACCESS_TOKEN", retry.getHeader(AUTHORIZATION))
    }

    @Test
    fun rejectedRefreshClearsAuthSessionAndDoesNotRetryProtectedRequest() {
        val store = FakeTokenStore(oldSnapshot())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path) {
                    REFRESH_PATH -> MockResponse()
                        .setResponseCode(401)
                        .setBody("Unauthorized")
                    PROTECTED_PATH -> MockResponse().setResponseCode(401)
                    else -> MockResponse().setResponseCode(404)
                }
        }

        val response = authenticatedClient(store)
            .newCall(protectedRequest())
            .execute()

        response.use {
            assertEquals(401, it.code)
        }

        assertNull(store.snapshot())
        assertEquals(1, store.clearCalls)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun temporaryRefreshFailureKeepsSessionForLaterRecovery() {
        val store = FakeTokenStore(oldSnapshot())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path) {
                    REFRESH_PATH -> MockResponse()
                        .setResponseCode(503)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "code": "SERVICE_UNAVAILABLE",
                              "message": "Try again later"
                            }
                            """.trimIndent()
                        )
                    PROTECTED_PATH -> MockResponse().setResponseCode(401)
                    else -> MockResponse().setResponseCode(404)
                }
        }

        val response = authenticatedClient(store)
            .newCall(protectedRequest())
            .execute()

        response.use {
            assertEquals(401, it.code)
        }

        assertEquals(oldSnapshot(), store.snapshot())
        assertEquals(0, store.clearCalls)
        assertEquals(2, server.requestCount)
    }

    private fun authenticatedClient(store: TokenStore): OkHttpClient {
        val refreshRetrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(
                TokenAuthenticator(
                    tokenStore = store,
                    refreshClient = createTokenRefreshClient(refreshRetrofit)
                )
            )
            .build()
    }

    private fun protectedRequest(): Request =
        Request.Builder()
            .url(server.url(PROTECTED_PATH))
            .build()

    private fun successfulRefreshResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "session": {
                    "accessToken": "$NEW_ACCESS_TOKEN",
                    "refreshToken": "$NEW_REFRESH_TOKEN",
                    "accessTokenExpiresAt": "2026-08-23T12:00:00Z",
                    "refreshTokenExpiresAt": "2026-09-23T12:00:00Z",
                    "complete": true
                  },
                  "user": {
                    "id": "user-id",
                    "email": "user@example.test",
                    "phone": null,
                    "username": "user",
                    "displayName": "User",
                    "avatarURL": null,
                    "emailVerified": true,
                    "phoneVerified": false,
                    "settings": null
                  }
                }
                """.trimIndent()
            )

    private fun oldSnapshot(): TokenSnapshot =
        TokenSnapshot(
            accessToken = OLD_ACCESS_TOKEN,
            refreshToken = OLD_REFRESH_TOKEN
        )

    private fun newSnapshot(): TokenSnapshot =
        TokenSnapshot(
            accessToken = NEW_ACCESS_TOKEN,
            refreshToken = NEW_REFRESH_TOKEN
        )

    private class FakeTokenStore(
        initialSnapshot: TokenSnapshot?
    ) : TokenStore {

        private val lock = Any()
        private var storedSnapshot = initialSnapshot

        var clearCalls = 0
            private set

        override suspend fun getAccessToken(): String? =
            synchronized(lock) {
                storedSnapshot?.accessToken
            }

        override suspend fun getTokenSnapshot(): TokenSnapshot? =
            synchronized(lock) {
                storedSnapshot
            }

        override suspend fun clearSessionIfMatches(
            expected: TokenSnapshot
        ): Boolean =
            synchronized(lock) {
                clearCalls += 1

                if (storedSnapshot == expected) {
                    storedSnapshot = null
                    true
                } else {
                    false
                }
            }

        override suspend fun updateTokens(
            accessToken: String,
            refreshToken: String
        ) {
            synchronized(lock) {
                storedSnapshot = TokenSnapshot(
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            }
        }

        fun snapshot(): TokenSnapshot? =
            synchronized(lock) {
                storedSnapshot
            }
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val PROTECTED_PATH = "/protected"
        const val REFRESH_PATH = "/api/v1/auth/refresh"
        const val OLD_ACCESS_TOKEN = "access-old"
        const val OLD_REFRESH_TOKEN = "refresh-old"
        const val NEW_ACCESS_TOKEN = "access-new"
        const val NEW_REFRESH_TOKEN = "refresh-new"
    }
}
