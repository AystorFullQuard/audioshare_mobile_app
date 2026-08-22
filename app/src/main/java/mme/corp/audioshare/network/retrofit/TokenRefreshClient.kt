package mme.corp.audioshare.network.retrofit

import mme.corp.audioshare.data.dto.RefreshRequest
import mme.corp.audioshare.data.dto.LoginResponse
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

fun interface TokenRefreshClient {
    fun refresh(refreshToken: String): Result<LoginResponse>
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

    override fun refresh(refreshToken: String): Result<LoginResponse> {
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
            check(response.accessToken.isNotBlank()) {
                "Refresh response access token is blank"
            }
            check(response.refreshToken.isNotBlank()) {
                "Refresh response refresh token is blank"
            }
            response
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
