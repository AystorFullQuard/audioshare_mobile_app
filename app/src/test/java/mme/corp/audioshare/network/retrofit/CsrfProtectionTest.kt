package mme.corp.audioshare.network.retrofit

import okhttp3.HttpUrl.Companion.toHttpUrl
import mme.corp.audioshare.data.storage.AccessTokenProvider
import mme.corp.audioshare.network.interceptor.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CsrfProtectionTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clients(): Pair<OkHttpClient, OkHttpClient> {
        val url = server.url("/")
        val cookies = CsrfCookieJar(url)
        val bare = OkHttpClient.Builder()
            .cookieJar(cookies).followRedirects(false).build()
        val proof = CsrfProofProvider(url, bare, cookies)
        val csrfClient = bare.newBuilder().addInterceptor(CsrfInterceptor(proof)).build()
        return bare to csrfClient
    }

    @Test
    fun authPostFetchesFreshProofAndClearsCookieAfterEachRequest() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/api/v1/auth/csrf") {
                    MockResponse().setResponseCode(200)
                        .addHeader(
                            "Set-Cookie",
                            "XSRF-TOKEN=cookie-secret; Path=/; HttpOnly; SameSite=Strict"
                        )
                        .setBody("""{"headerName":"X-XSRF-TOKEN","token":"masked-secret"}""")
                } else {
                    assertEquals("masked-secret", request.getHeader("X-XSRF-TOKEN"))
                    assertEquals("XSRF-TOKEN=cookie-secret", request.getHeader("Cookie"))
                    assertNull(request.getHeader("Authorization"))
                    MockResponse().setResponseCode(200)
                }
        }
        val (_, client) = clients()
        repeat(2) {
            client.newCall(
                Request.Builder().url(server.url("/api/v1/auth/login"))
                    .post("{}".toRequestBody()).build()
            ).execute().use {
                assertEquals(200, it.code)
            }
        }
        // Every auth write fetches its own proof; no token/cookie survives the transaction.
        assertEquals(4, server.requestCount)
    }

    @Test
    fun protectedWriteCarriesBothJwtAndCsrfWhilePublicLoginCarriesOnlyCsrf() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/api/v1/auth/csrf") {
                    MockResponse().setResponseCode(200)
                        .addHeader("Set-Cookie", "XSRF-TOKEN=csrf-cookie; Path=/; HttpOnly")
                        .setBody("""{"headerName":"X-XSRF-TOKEN","token":"csrf-mask"}""")
                } else {
                    assertEquals("csrf-mask", request.getHeader("X-XSRF-TOKEN"))
                    assertEquals("XSRF-TOKEN=csrf-cookie", request.getHeader("Cookie"))
                    MockResponse().setResponseCode(200)
                }
        }
        val (bare, _) = clients()
        // This test uses a separate proof provider on the same bare client's cookie jar.
        val jar = CsrfCookieJar(server.url("/"))
        val isolatedBare = bare.newBuilder().cookieJar(jar).build()
        val csrf = CsrfInterceptor(CsrfProofProvider(server.url("/"), isolatedBare, jar))
        val client = isolatedBare.newBuilder()
            .addInterceptor(AuthInterceptor(object : AccessTokenProvider {
                override suspend fun getAccessToken(): String = "access-jwt"
            }))
            .addInterceptor(csrf)
            .build()
        client.newCall(
            Request.Builder().url(server.url("/api/v1/auth/login"))
                .post("{}".toRequestBody()).build()
        ).execute().close()
        client.newCall(
            Request.Builder().url(server.url("/api/v1/devices/register"))
                .post("{}".toRequestBody()).build()
        ).execute().close()
        assertEquals(4, server.requestCount)
        assertEquals("/api/v1/auth/csrf", server.takeRequest().path)
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals("/api/v1/auth/csrf", server.takeRequest().path)
        assertEquals("Bearer access-jwt", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun authTransactionDiscardsCookieAndFetchesFreshProofAfterProtectedWrite() {
        var fetched = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/v1/auth/csrf") {
                    fetched++
                    return MockResponse().setResponseCode(200)
                        .addHeader("Set-Cookie", "XSRF-TOKEN=cookie-$fetched; Path=/; HttpOnly")
                        .setBody("""{"headerName":"X-XSRF-TOKEN","token":"proof-$fetched"}""")
                }
                assertEquals("proof-$fetched", request.getHeader("X-XSRF-TOKEN"))
                assertEquals("XSRF-TOKEN=cookie-$fetched", request.getHeader("Cookie"))
                return MockResponse().setResponseCode(204)
            }
        }
        val (bare, client) = clients()
        client.newCall(
            Request.Builder().url(server.url("/api/v1/devices/register"))
                .post("{}".toRequestBody()).build()
        ).execute().close()
        client.newCall(
            Request.Builder().url(server.url("/api/v1/auth/login"))
                .post("{}".toRequestBody()).build()
        ).execute().close()
        assertTrue(bare.cookieJar.loadForRequest(server.url("/api/v1/auth/login")).isEmpty())
        assertEquals(2, fetched)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun getDoesNotTriggerCsrfAndCookieNeverEscapesOrigin() {
        server.enqueue(MockResponse().setResponseCode(200))
        val (bare, client) = clients()
        client.newCall(
            Request.Builder().url(server.url("/api/v1/rooms"))
                .get().build()
        ).execute().use { assertEquals(200, it.code) }
        assertEquals(1, server.requestCount)
        assertNull(server.takeRequest().getHeader("X-XSRF-TOKEN"))
        val cookies = CsrfCookieJar(server.url("/"))
        cookies.saveFromResponse(
            server.url("/"),
            listOf(okhttp3.Cookie.parse(server.url("/"), "XSRF-TOKEN=local; Path=/")!!)
        )
        assertTrue(cookies.loadForRequest(server.url("/api/v1/auth/login")).isNotEmpty())
        assertTrue(cookies.loadForRequest("https://elsewhere.example/".toHttpUrl()).isEmpty())
        assertTrue(
            bare.cookieJar.loadForRequest("https://elsewhere.example/".toHttpUrl()).isEmpty()
        )
    }

    @Test
    fun rejectsMissingCookieBeforeSendingCredentials() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"headerName":"X-XSRF-TOKEN","token":"masked"}""")
        )
        val (_, client) = clients()
        val result = runCatching {
            client.newCall(
                Request.Builder().url(server.url("/api/v1/auth/refresh"))
                    .post("{}".toRequestBody()).build()
            ).execute().close()
        }
        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
        assertFalse(result.exceptionOrNull().toString().contains("masked"))
    }

    @Test
    fun invalidatedServerProofIsRefetchedOnNextRequestWithoutReplayingWrite() {
        var fetches = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/api/v1/auth/csrf") {
                    fetches++
                    MockResponse().setResponseCode(200)
                        .addHeader("Set-Cookie", "XSRF-TOKEN=cookie-secret; Path=/; HttpOnly")
                        .setBody("""{"headerName":"X-XSRF-TOKEN","token":"masked-$fetches"}""")
                } else if (fetches == 1) {
                    MockResponse().setResponseCode(403)
                        .setBody("""{"code":"CSRF_TOKEN_INVALID"}""")
                } else MockResponse().setResponseCode(204)
        }
        val (_, client) = clients()
        val request = Request.Builder().url(server.url("/api/v1/auth/refresh"))
            .post("{}".toRequestBody()).build()
        client.newCall(request).execute().use { assertEquals(403, it.code) }
        client.newCall(request).execute().use { assertEquals(204, it.code) }
        assertEquals(2, fetches)
        assertEquals(4, server.requestCount)
    }
}
