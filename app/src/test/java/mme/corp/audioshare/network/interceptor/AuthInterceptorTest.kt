package mme.corp.audioshare.network.interceptor

import mme.corp.audioshare.data.storage.AccessTokenProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
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
    fun attachesBearerTokenOnlyToAuthenticatedEndpoints() {
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(200))
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(
                    AccessTokenProvider { "access-token" }
                )
            )
            .build()

        execute(client, "/api/v1/auth/login")
        execute(client, "/api/v1/auth/refresh")
        execute(client, "/api/v1/bootstrap")
        execute(client, "/api/v1/presence/heartbeat")

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals(
            "Bearer access-token",
            server.takeRequest().getHeader("Authorization")
        )
        assertEquals(
            "Bearer access-token",
            server.takeRequest().getHeader("Authorization")
        )
    }

    private fun execute(client: OkHttpClient, path: String) {
        client.newCall(
            Request.Builder()
                .url(server.url(path))
                .build()
        ).execute().use { response ->
            assertEquals(200, response.code)
        }
    }
}
