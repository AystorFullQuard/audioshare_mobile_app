package mme.corp.audioshare.network.retrofit

import com.google.gson.Gson
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Process-local, origin-bound CSRF cookie store. CSRF cookies are not user sessions;
 * never persist them, and never accept or forward arbitrary server cookies.
 */
internal class CsrfCookieJar(private val apiOrigin: HttpUrl) : CookieJar {
    private var csrfCookie: Cookie? = null

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!sameOrigin(url)) return
        cookies.lastOrNull { it.name == COOKIE_NAME }?.let { csrfCookie = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (sameOrigin(url)) {
            listOfNotNull(csrfCookie?.takeIf { it.matches(url) })
        } else {
            emptyList()
        }

    @Synchronized
    fun clear() {
        csrfCookie = null
    }

    private fun sameOrigin(url: HttpUrl): Boolean =
        url.scheme == apiOrigin.scheme &&
                url.host == apiOrigin.host && url.port == apiOrigin.port

    private companion object {
        const val COOKIE_NAME = "XSRF-TOKEN"
    }
}

/** The header value is masked by Spring; it must be paired with its HttpOnly cookie. */
internal data class CsrfProof(val headerName: String, val token: String)

internal class CsrfProofProvider(
    private val baseUrl: HttpUrl,
    private val bootstrapClient: OkHttpClient,
    private val cookies: CsrfCookieJar
) {
    private val gson = Gson()
    private val lock = Any()
    private var cached: CsrfProof? = null

    fun proof(): CsrfProof = synchronized(lock) {
        cached?.let { proof ->
            if (cookies.loadForRequest(baseUrl.resolve("/api/v1/auth/csrf")!!)
                    .isNotEmpty()
            ) return@synchronized proof
        }
        val csrfUrl = baseUrl.resolve("/api/v1/auth/csrf")
            ?: throw IOException("Invalid CSRF endpoint")
        val request = Request.Builder()
            .url(csrfUrl)
            .get()
            .header("Cache-Control", "no-store")
            .build()
        bootstrapClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("CSRF initialization returned HTTP ${response.code}")
            }
            val content = response.body?.charStream()
                ?: throw IOException("Missing CSRF response")
            val received = try {
                gson.fromJson(content, CsrfProof::class.java)
                    ?: throw IOException("CSRF response did not contain proof")
            } catch (exception: RuntimeException) {
                throw IOException("Invalid CSRF response", exception)
            }
            // The header must never be selected freely from an untrusted response.
            if (received.headerName != "X-XSRF-TOKEN" ||
                received.token.isNullOrBlank() ||
                received.token.length > 4096 ||
                cookies.loadForRequest(csrfUrl).isEmpty()
            ) {
                throw IOException("CSRF response did not contain matching proof and cookie")
            }
            cached = received
            received
        }
    }

    fun invalidate() = synchronized(lock) {
        cached = null
        cookies.clear()
    }
}

/**
 * Shared by the authenticated Retrofit client and the separate no-bearer refresh client.
 * GET /csrf uses the bare bootstrap client, avoiding recursion and leaked JWTs.
 */
internal class CsrfInterceptor(private val proofProvider: CsrfProofProvider) : Interceptor {
    // Auth operations are serialized so one response cannot clear another auth request's proof.
    private val authLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!needsCsrf(request)) return chain.proceed(request)
        if (request.url.encodedPath in AUTH_PATHS) {
            return synchronized(authLock) {
                proofProvider.invalidate() // fresh handshake for every auth transaction
                proceedWithProof(chain, clearAfter = true)
            }
        }
        return proceedWithProof(chain, clearAfter = false)
    }

    private fun proceedWithProof(chain: Interceptor.Chain, clearAfter: Boolean): Response {
        try {
            val proof = proofProvider.proof() // GET /auth/csrf before sending credentials
            val request = chain.request().newBuilder()
                .header(proof.headerName, proof.token)
                .header("Cache-Control", "no-store")
                .build()
            val response = chain.proceed(request)
            if (response.code == 403 &&
                response.peekBody(4096).string().contains("CSRF_TOKEN_INVALID")
            ) {
                proofProvider.invalidate() // no automatic replay of unsafe requests
            }
            return response
        } finally {
            if (clearAfter) proofProvider.invalidate() // drop ephemeral proof AND cookie
        }
    }

    private companion object {
        val AUTH_PATHS = setOf(
            "/api/v1/auth/login", "/api/v1/auth/register",
            "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/dev-login", "/api/v1/auth/dev-register"
        )
    }

    private fun needsCsrf(request: Request): Boolean =
        request.url.encodedPath.startsWith("/api/v1/") &&
                request.method in setOf("POST", "PUT", "PATCH", "DELETE")
}
