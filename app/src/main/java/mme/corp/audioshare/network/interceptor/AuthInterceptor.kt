package mme.corp.audioshare.network.interceptor

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mme.corp.audioshare.data.storage.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionManager: SessionManager
) : Interceptor {

    companion object {
        private const val AUTHORIZATION = "Authorization"
        private const val BEARER = "Bearer"

        private val UNAUTHENTICATED_PATHS = setOf(
            "/auth/login",
            "/auth/register",
            "/bootstrap"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {

        val request = chain.request()

        if (isUnauthenticated(request.url.encodedPath)) {
            return chain.proceed(request)
        }

        val token = runBlocking {
            sessionManager.accessToken.first()
        }

        if (token.isNullOrBlank()) {
            return chain.proceed(request)
        }

        return chain.proceed(
            request.newBuilder()
                .header(AUTHORIZATION, "$BEARER $token")
                .build()
        )
    }

    private fun isUnauthenticated(path: String): Boolean =
        UNAUTHENTICATED_PATHS.any(path::endsWith)
}