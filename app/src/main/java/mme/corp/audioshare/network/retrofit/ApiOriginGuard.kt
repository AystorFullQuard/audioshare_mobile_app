package mme.corp.audioshare.network.retrofit

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Stops accidental JWT/CSRF disclosure if an absolute Retrofit URL is ever added. */
internal class ApiOriginGuard(private val origin: HttpUrl) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.scheme != origin.scheme || url.host != origin.host || url.port != origin.port) {
            throw IOException("Refusing API request outside configured ServerRelay origin")
        }
        return chain.proceed(chain.request())
    }
}
