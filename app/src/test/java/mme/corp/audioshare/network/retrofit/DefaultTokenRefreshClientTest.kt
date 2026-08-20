package mme.corp.audioshare.network.retrofit

import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DefaultTokenRefreshClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: TokenRefreshClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        client = createTokenRefreshClient(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun refreshUsesDedicatedEndpointWithoutAuthorizationHeader() {
        server.enqueue(successfulRefreshResponse())

        val result = client.refresh("refresh-old")

        assertTrue(result.isSuccess)
        assertEquals("access-new", result.getOrThrow().accessToken)
        assertEquals("refresh-new", result.getOrThrow().refreshToken)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/refresh", request.path)
        assertNull(request.getHeader("Authorization"))

        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("refresh-old", body["refreshToken"].asString)
    }

    @Test
    fun blankRefreshTokenIsRejectedBeforeNetworkCall() {
        val result = client.refresh("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun unauthorizedRefreshIsClassifiedAsRejectedSession() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )

        val result = client.refresh("refresh-old")

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull() is RefreshSessionRejectedException
        )
    }

    @Test
    fun malformedSuccessfulResponseIsRejected() {
        server.enqueue(
            successfulRefreshResponse(
                accessToken = "",
                refreshToken = "refresh-new"
            )
        )

        val blankAccess = client.refresh("refresh-old")

        assertTrue(blankAccess.isFailure)
        assertTrue(blankAccess.exceptionOrNull() is IllegalStateException)

        server.enqueue(
            successfulRefreshResponse(
                accessToken = "access-new",
                refreshToken = ""
            )
        )

        val blankRefresh = client.refresh("refresh-old")

        assertTrue(blankRefresh.isFailure)
        assertTrue(blankRefresh.exceptionOrNull() is IllegalStateException)
    }

    private fun successfulRefreshResponse(
        accessToken: String = "access-new",
        refreshToken: String = "refresh-new"
    ): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "userId": "user-id",
                  "sessionId": "session-id",
                  "tokenType": "Bearer",
                  "accessToken": "$accessToken",
                  "refreshToken": "$refreshToken"
                }
                """.trimIndent()
            )
}
