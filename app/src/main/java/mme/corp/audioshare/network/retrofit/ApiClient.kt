package mme.corp.audioshare.network.retrofit

import android.util.Log
import mme.corp.audioshare.BuildConfig
import mme.corp.audioshare.data.storage.TokenStore
import mme.corp.audioshare.network.interceptor.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "ApiClient"
    private var retrofit: Retrofit? = null

    fun initialize(tokenStore: TokenStore) {
        Log.d(TAG, "Initializing Retrofit")
        val logging = HttpLoggingInterceptor().apply {
            level =
                if (BuildConfig.DEBUG)
                    HttpLoggingInterceptor.Level.BODY
                else
                    HttpLoggingInterceptor.Level.NONE
        }

        val sharedClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val refreshClient = createTokenRefreshClient(
            buildRetrofit(sharedClient)
        )

        val authenticatedClient = sharedClient.newBuilder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(
                TokenAuthenticator(
                    tokenStore = tokenStore,
                    refreshClient = refreshClient
                )
            )
            .addInterceptor(logging)
            .build()

        retrofit = buildRetrofit(authenticatedClient)
    }

    fun <T> create(service: Class<T>): T {
        val instance = retrofit
            ?: error("ApiClient.initialize() was not called.")

        return instance.create(service)
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(NetworkConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}