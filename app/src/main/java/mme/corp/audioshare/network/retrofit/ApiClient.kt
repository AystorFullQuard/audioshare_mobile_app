package mme.corp.audioshare.network.retrofit

import mme.corp.audioshare.data.storage.TokenStore
import mme.corp.audioshare.network.interceptor.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var retrofit: Retrofit? = null

    fun initialize(tokenStore: TokenStore) {
        // Fail before constructing any client if the build is using an invalid endpoint.
        val baseUrl = NetworkConfig.BASE_URL

        // Do not log auth headers, CSRF cookies, tokens, user data, or call metadata.
        // Later: scoped redacted diagnostics via OBS/LOG, never a BODY logger.
        val csrfCookies = CsrfCookieJar(baseUrl.toHttpUrl())
        val sharedClient = OkHttpClient.Builder()
            .addInterceptor(ApiOriginGuard(baseUrl.toHttpUrl()))
            .cookieJar(csrfCookies)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val csrfProof = CsrfProofProvider(
            baseUrl = baseUrl.toHttpUrl(),
            bootstrapClient = sharedClient,
            cookies = csrfCookies
        )
        val csrfInterceptor = CsrfInterceptor(csrfProof)

        // Refresh requests have no bearer token, but they still require CSRF.
        val refreshClient = createTokenRefreshClient(
            buildRetrofit(
                sharedClient.newBuilder()
                    .addInterceptor(csrfInterceptor)
                    .build(), baseUrl
            )
        )

        val authenticatedClient = sharedClient.newBuilder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(csrfInterceptor)
            .authenticator(
                TokenAuthenticator(
                    tokenStore = tokenStore,
                    refreshClient = refreshClient
                )
            )
            .build()

        retrofit = buildRetrofit(authenticatedClient, baseUrl)
    }

    fun <T> create(service: Class<T>): T {
        val instance = retrofit
            ?: error("ApiClient.initialize() was not called.")
        return instance.create(service)
    }

    private fun buildRetrofit(client: OkHttpClient, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
