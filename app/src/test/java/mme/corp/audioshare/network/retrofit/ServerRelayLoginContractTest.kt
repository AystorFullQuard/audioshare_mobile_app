package mme.corp.audioshare.network.retrofit

import kotlinx.coroutines.runBlocking
import mme.corp.audioshare.data.api.AuthApi
import mme.corp.audioshare.data.dto.LoginRequest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Test the real Retrofit login contract and Spring-compatible masked-CSRF handshake. */
class ServerRelayLoginContractTest {
    @Test
    fun csrfPreflightPrecedesLoginAndResponseCanBeParsed() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/api/v1/auth/csrf" -> MockResponse().setResponseCode(200)
                            .addHeader(
                                "Set-Cookie",
                                "XSRF-TOKEN=csrf-cookie; Path=/; HttpOnly; SameSite=Strict"
                            )
                            .addHeader("Content-Type", "application/json")
                            .setBody("""{"headerName":"X-XSRF-TOKEN","token":"masked-proof"}""")

                        "/api/v1/auth/login" -> {
                            assertEquals("masked-proof", request.getHeader("X-XSRF-TOKEN"))
                            assertEquals("XSRF-TOKEN=csrf-cookie", request.getHeader("Cookie"))
                            assertNull(request.getHeader("Authorization"))
                            assertTrue(request.body.readUtf8().contains("\"login\":\"someone\""))
                            MockResponse().setResponseCode(200)
                                .addHeader("Content-Type", "application/json")
                                .setBody(
                                    """{
                                "session":{"accessToken":"access-jwt","refreshToken":"refresh-jwt",
                                  "accessTokenExpiresAt":"2026-10-29T16:00:00Z",
                                  "refreshTokenExpiresAt":"2026-10-30T16:00:00Z", "complete":true},
                                "user":{"id":"user-id","username":"someone","displayName":"Example User",
                                  "email":null,"phone":null,"avatarURL":null,
                                  "emailVerified":true,"phoneVerified":false,"settings":null}
                            }"""
                                )
                        }

                        else -> MockResponse().setResponseCode(404)
                    }
            }
            val origin = server.url("/")
            val jar = CsrfCookieJar(origin)
            val bare = OkHttpClient.Builder().cookieJar(jar)
                .followRedirects(false).followSslRedirects(false).build()
            val proof = CsrfProofProvider(origin, bare, jar)
            val client = bare.newBuilder().addInterceptor(CsrfInterceptor(proof)).build()
            val api = Retrofit.Builder().baseUrl(origin).client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(AuthApi::class.java)
            val response = api.login(LoginRequest("someone", "password1234"))
            assertEquals(200, response.code())
            assertEquals("user-id", response.body()?.user?.id)
            assertEquals("access-jwt", response.body()?.session?.accessToken)
            assertEquals(2, server.requestCount)
            assertTrue(jar.loadForRequest(server.url("/api/v1/auth/csrf")).isEmpty())
        } finally {
            server.shutdown()
        }
    }
}
