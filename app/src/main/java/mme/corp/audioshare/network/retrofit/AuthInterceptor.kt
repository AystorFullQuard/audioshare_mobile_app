package mme.corp.audioshare.network.retrofit

import kotlinx.coroutines.runBlocking
import mme.corp.audioshare.storage.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionManager: SessionManager
) : Interceptor {

    private val PUBLIC_ENDPOINTS = setOf(
        "/auth/login",
        "/auth/register",
        "/bootstrap"
    )

    override fun intercept(chain: Interceptor.Chain): Response {

        val originalRequest = chain.request()

        // DataStore is asynchronous, but OkHttp interceptors are synchronous.
        val token = runBlocking {
            sessionManager.getAccessToken()
        }

        val path = originalRequest.url.encodedPath

        if (path in PUBLIC_ENDPOINTS) {
            return chain.proceed(originalRequest)
        }

        // If no token exists (e.g. login), send the request unchanged.
        if (token.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}