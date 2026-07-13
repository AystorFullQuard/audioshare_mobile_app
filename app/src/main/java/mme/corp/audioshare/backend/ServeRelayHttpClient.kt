package mme.corp.audioshare.backend

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import mme.corp.audioshare.backend.auth.AuthApi
import mme.corp.audioshare.backend.bootstrap.BootstrapApi
import mme.corp.audioshare.backend.devices.DevicesApi
import mme.corp.audioshare.backend.presence.PresenceApi
import mme.corp.audioshare.backend.rooms.RoomsApi
import mme.corp.audioshare.backend.storage.SessionStore
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ServeRelayHttpClient private constructor(
    val json: Json,
    val okHttpClient: OkHttpClient,
    val authApi: AuthApi,
    val bootstrapApi: BootstrapApi,
    val presenceApi: PresenceApi,
    val devicesApi: DevicesApi,
    val roomsApi: RoomsApi
) {
    companion object {
        fun create(
            config: ServeRelayConfig,
            sessionStore: SessionStore
        ): ServeRelayHttpClient {
            val json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
                coerceInputValues = true
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(config.writeTimeoutSeconds, TimeUnit.SECONDS)
                .addInterceptor(BearerTokenInterceptor(sessionStore))
                .apply {
                    if (config.enableHttpLogging) {
                        addInterceptor(
                            HttpLoggingInterceptor().apply {
                                level = HttpLoggingInterceptor.Level.BASIC
                                redactHeader(AUTHORIZATION_HEADER)
                            }
                        )
                    }
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(okHttpClient)
                .addConverterFactory(
                    json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType())
                )
                .build()

            return ServeRelayHttpClient(
                json = json,
                okHttpClient = okHttpClient,
                authApi = retrofit.create(AuthApi::class.java),
                bootstrapApi = retrofit.create(BootstrapApi::class.java),
                presenceApi = retrofit.create(PresenceApi::class.java),
                devicesApi = retrofit.create(DevicesApi::class.java),
                roomsApi = retrofit.create(RoomsApi::class.java)
            )
        }

        private const val JSON_MEDIA_TYPE = "application/json"
        private const val AUTHORIZATION_HEADER = "Authorization"
    }
}

private class BearerTokenInterceptor(
    private val sessionStore: SessionStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        if (path in PUBLIC_AUTH_PATHS) {
            return chain.proceed(
                originalRequest.newBuilder()
                    .removeHeader(AUTHORIZATION_HEADER)
                    .build()
            )
        }

        val token = sessionStore.currentAccessToken()
        val request = if (token.isNullOrBlank()) {
            originalRequest
        } else {
            originalRequest
                .newBuilder()
                .header(AUTHORIZATION_HEADER, "Bearer $token")
                .build()
        }

        return chain.proceed(request)
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        val PUBLIC_AUTH_PATHS = setOf(
            "/api/v1/auth/dev-register",
            "/api/v1/auth/dev-login",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh"
        )
    }
}
