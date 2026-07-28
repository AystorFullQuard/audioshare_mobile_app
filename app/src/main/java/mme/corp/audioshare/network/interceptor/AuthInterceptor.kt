package mme.corp.audioshare.network.interceptor

import kotlinx.coroutines.runBlocking
import mme.corp.audioshare.data.storage.AccessTokenProvider
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val accessTokenProvider: AccessTokenProvider
) : Interceptor {

    companion object {
        private const val AUTHORIZATION = "Authorization"
        private const val BEARER = "Bearer"

        private val UNAUTHENTICATED_PATHS = setOf(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {

        val request = chain.request()

        if (isUnauthenticated(request.url.encodedPath)) {
            return chain.proceed(request)
        }

        val token = runBlocking {
            accessTokenProvider.getAccessToken()
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
        path in UNAUTHENTICATED_PATHS
}
