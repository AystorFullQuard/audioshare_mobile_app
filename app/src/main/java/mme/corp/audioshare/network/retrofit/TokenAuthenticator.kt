package mme.corp.audioshare.network.retrofit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import mme.corp.audioshare.data.storage.TokenSnapshot
import mme.corp.audioshare.data.storage.TokenStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshClient: TokenRefreshClient
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(
        route: Route?,
        response: Response
    ): Request? {
        if (!canRecover(response)) {
            return null
        }

        val failedAccessToken = response.request.bearerAccessToken()
            ?: return null

        val snapshot = readTokenSnapshot()
            ?: return null

        if (snapshot.accessToken != failedAccessToken) {
            return response.request.withAccessToken(snapshot.accessToken)
        }

        return synchronized(refreshLock) {
            recoverUnderLock(
                response = response,
                failedAccessToken = failedAccessToken
            )
        }
    }

    private fun recoverUnderLock(
        response: Response,
        failedAccessToken: String
    ): Request? {
        val latestSnapshot = readTokenSnapshot()
            ?: return null

        if (latestSnapshot.accessToken != failedAccessToken) {
            return response.request.withAccessToken(
                latestSnapshot.accessToken
            )
        }

        val refreshResult = refreshClient.refresh(
            latestSnapshot.refreshToken
        )

        if (refreshResult.isFailure) {
            if (refreshResult.exceptionOrNull().isRefreshSessionRejected()) {
                clearSessionIfUnchanged(latestSnapshot)
            }
            return null
        }

        val refreshed = refreshResult.getOrThrow()

        if (!persistTokens(refreshed.accessToken, refreshed.refreshToken)) {
            return null
        }

        return response.request.withAccessToken(refreshed.accessToken)
    }

    private fun canRecover(response: Response): Boolean =
        response.code == HTTP_UNAUTHORIZED &&
            response.request.url.encodedPath != REFRESH_PATH &&
            !response.hasPriorUnauthorizedResponse()

    private fun readTokenSnapshot(): TokenSnapshot? = try {
        runBlocking {
            tokenStore.getTokenSnapshot()
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (_: Exception) {
        null
    }

    private fun persistTokens(
        accessToken: String,
        refreshToken: String
    ): Boolean = try {
        runBlocking {
            tokenStore.updateTokens(
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        }
        true
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    } catch (_: Exception) {
        false
    }

    private fun clearSessionIfUnchanged(
        expected: TokenSnapshot
    ) {
        try {
            runBlocking {
                tokenStore.clearSessionIfMatches(expected)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            // The failed request remains unauthorized. Session cleanup can be
            // retried by a later request if persistence becomes available.
        }
    }

    private fun Throwable?.isRefreshSessionRejected(): Boolean =
        this is RefreshSessionRejectedException

    private fun Request.bearerAccessToken(): String? {
        val authorization = header(AUTHORIZATION)
            ?: return null
        val prefix = "$BEARER "

        if (!authorization.startsWith(prefix, ignoreCase = true)) {
            return null
        }

        return authorization
            .substring(prefix.length)
            .takeIf(String::isNotBlank)
    }

    private fun Request.withAccessToken(accessToken: String): Request =
        newBuilder()
            .header(AUTHORIZATION, "$BEARER $accessToken")
            .build()

    private fun Response.hasPriorUnauthorizedResponse(): Boolean {
        var previous = priorResponse

        while (previous != null) {
            if (previous.code == HTTP_UNAUTHORIZED) {
                return true
            }
            previous = previous.priorResponse
        }

        return false
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer"
        const val REFRESH_PATH = "/api/v1/auth/refresh"
    }
}