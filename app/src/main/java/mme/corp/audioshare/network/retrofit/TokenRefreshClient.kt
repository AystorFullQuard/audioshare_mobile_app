package mme.corp.audioshare.network.retrofit

import mme.corp.audioshare.data.dto.LoginResponse
import mme.corp.audioshare.data.dto.RefreshRequest
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

data class RefreshedTokens(
    val accessToken: String,
    val refreshToken: String
)

fun interface TokenRefreshClient {
    fun refresh(refreshToken: String): Result<RefreshedTokens>
}

internal class RefreshSessionRejectedException :
    Exception("Refresh session was rejected")

internal fun createTokenRefreshClient(
    retrofit: Retrofit
): TokenRefreshClient = DefaultTokenRefreshClient(
    refreshApi = retrofit.create(TokenRefreshApi::class.java)
)

internal fun interface TokenRefreshApi {
    @POST("/api/v1/auth/refresh")
    fun refresh(
        @Body request: RefreshRequest
    ): Call<LoginResponse>
}

internal class DefaultTokenRefreshClient(
    private val refreshApi: TokenRefreshApi
) : TokenRefreshClient {

    override fun refresh(refreshToken: String): Result<RefreshedTokens> {
        if (refreshToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Refresh token must not be blank")
            )
        }

        return executeBlockingApiCall(
            emptyBodyMessage = "Refresh response body is empty",
            httpFailureOverride = { response ->
                if (response.code() == HTTP_UNAUTHORIZED) {
                    RefreshSessionRejectedException()
                } else {
                    null
                }
            }
        ) {
            refreshApi.refresh(
                RefreshRequest(refreshToken = refreshToken)
            ).execute()
        }.mapCatching { response ->
            val session = response.session

            check(session.accessToken.isNotBlank()) {
                "Refresh response access token is blank"
            }
            check(session.refreshToken.isNotBlank()) {
                "Refresh response refresh token is blank"
            }

            RefreshedTokens(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken
            )
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
