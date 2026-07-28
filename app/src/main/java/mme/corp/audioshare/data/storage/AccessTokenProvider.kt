package mme.corp.audioshare.data.storage

/**
 * Read-only access to the current access token for authenticated HTTP requests.
 */
fun interface AccessTokenProvider {
    suspend fun getAccessToken(): String?
}
