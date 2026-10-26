package mme.corp.audioshare.network.retrofit

import mme.corp.audioshare.BuildConfig

/** A single immutable endpoint choice for the current build variant. */
object NetworkConfig {
    val BASE_URL: String by lazy {
        ApiEndpointPolicy.approvedBaseUrl(
            configuredUrl = BuildConfig.SERVERRELAY_BASE_URL,
            isDebug = BuildConfig.DEBUG
        )
    }
}
