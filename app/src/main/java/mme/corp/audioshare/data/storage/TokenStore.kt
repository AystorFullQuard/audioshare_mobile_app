package mme.corp.audioshare.data.storage

/**
 * Coherent access/refresh token pair read from one persistent-store snapshot.
 *
 * Keeping both values together prevents authentication recovery from combining
 * tokens that belong to different refresh generations.
 */
data class TokenSnapshot(
    val accessToken: String,
    val refreshToken: String
)

/**
 * Token persistence contract for authentication runtime components.
 */
interface TokenStore : AccessTokenProvider {
    suspend fun getTokenSnapshot(): TokenSnapshot?

    /**
     * Clears the complete local session only if the persisted token generation
     * still matches [expected].
     */
    suspend fun clearSessionIfMatches(
        expected: TokenSnapshot
    ): Boolean

    suspend fun updateTokens(
        accessToken: String,
        refreshToken: String
    )
}
