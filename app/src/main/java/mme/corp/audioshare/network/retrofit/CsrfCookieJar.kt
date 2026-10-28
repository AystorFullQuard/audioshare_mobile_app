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
            if (cookies.loadForRequest(baseUrl).isNotEmpty()) return@synchronized proof
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

    fun invalidate() = synchronized(lock) { cached = null }
}

/**
 * Shared by the authenticated Retrofit client and the separate no-bearer refresh client.
 * GET /csrf uses the bare bootstrap client, avoiding recursion and leaked JWTs.
 */
internal class CsrfInterceptor(private val proofProvider: CsrfProofProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!needsCsrf(request)) return chain.proceed(request)
        val proof = proofProvider.proof()
        val response = chain.proceed(
            request.newBuilder().header(proof.headerName, proof.token).build()
        )
        if (response.code == 403 &&
            response.peekBody(4096).string().contains("CSRF_TOKEN_INVALID")
        ) {
            // Do not replay writes implicitly. The next request negotiates a fresh proof.
            proofProvider.invalidate()
        }
        return response
    }

    private fun needsCsrf(request: Request): Boolean =
        request.url.encodedPath.startsWith("/api/v1/") &&
                request.method in setOf("POST", "PUT", "PATCH", "DELETE")
}
