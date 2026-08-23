package mme.corp.audioshare.network.retrofit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import mme.corp.audioshare.data.storage.TokenSnapshot
import mme.corp.audioshare.data.storage.TokenStore
import mme.corp.audioshare.exception.ApiErrorResponse
import mme.corp.audioshare.exception.ApiException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TokenAuthenticatorTest {

    @Test
    fun successfulRefreshUpdatesStoreAndRetriesWithNewAccessToken() {
        val store = FakeTokenStore(oldSnapshot())
        val refreshCalls = AtomicInteger()
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = { refreshToken ->
                refreshCalls.incrementAndGet()
                assertEquals(OLD_REFRESH_TOKEN, refreshToken)
                Result.success(refreshResponse())
            }
        )

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(OLD_ACCESS_TOKEN)
        )

        assertEquals(
            "Bearer $NEW_ACCESS_TOKEN",
            retriedRequest?.header("Authorization")
        )
        assertEquals(newSnapshot(), store.snapshot())
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun staleUnauthorizedUsesAlreadyRefreshedAccessTokenWithoutRefreshingAgain() {
        val store = FakeTokenStore(newSnapshot())
        val refreshCalls = AtomicInteger()
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = {
                refreshCalls.incrementAndGet()
                Result.failure(AssertionError("Refresh must not be called"))
            }
        )

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(OLD_ACCESS_TOKEN)
        )

        assertEquals(
            "Bearer $NEW_ACCESS_TOKEN",
            retriedRequest?.header("Authorization")
        )
        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun refreshFailureLeavesStoredTokensAndStopsRecovery() {
        val store = FakeTokenStore(oldSnapshot())
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = {
                Result.failure(IllegalStateException("Refresh failed"))
            }
        )

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(OLD_ACCESS_TOKEN)
        )

        assertNull(retriedRequest)
        assertEquals(oldSnapshot(), store.snapshot())
    }

    @Test
    fun rejectedRefreshClearsAuthenticationSession() {
        val store = FakeTokenStore(oldSnapshot())
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = {
                Result.failure(RefreshSessionRejectedException())
            }
        )

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(OLD_ACCESS_TOKEN)
        )

        assertNull(retriedRequest)
        assertNull(store.snapshot())
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun staleRejectedRefreshDoesNotClearNewerTokenGeneration() {
        val store = FakeTokenStore(oldSnapshot())
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = {
                runBlocking {
                    store.updateTokens(
                        accessToken = NEW_ACCESS_TOKEN,
                        refreshToken = NEW_REFRESH_TOKEN
                    )
                }
                Result.failure(RefreshSessionRejectedException())
            }
        )

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(OLD_ACCESS_TOKEN)
        )

        assertNull(retriedRequest)
        assertEquals(newSnapshot(), store.snapshot())
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun temporaryRefreshServerFailurePreservesAuthenticationSession() {
        val store = FakeTokenStore(oldSnapshot())
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = {
                Result.failure(
                    apiFailure(
                        httpCode = 503,
                        code = "SERVICE_UNAVAILABLE"
                    )
                )
            }
        )

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(OLD_ACCESS_TOKEN)
        )

        assertNull(retriedRequest)
        assertEquals(oldSnapshot(), store.snapshot())
        assertEquals(0, store.clearCalls)
    }

    @Test
    fun tokenStoreCancellationIsNotConvertedIntoAuthenticationFailure() {
        val expected = CancellationException("cancelled")
        val store = FakeTokenStore(
            initialSnapshot = oldSnapshot(),
            snapshotFailure = expected
        )
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = {
                Result.failure(AssertionError("Refresh must not be called"))
            }
        )

        try {
            authenticator.authenticate(
                route = null,
                response = unauthorizedResponse(OLD_ACCESS_TOKEN)
            )
            throw AssertionError("CancellationException must be rethrown")
        } catch (actual: CancellationException) {
            assertEquals(expected, actual)
        }
    }

    @Test
    fun secondUnauthorizedResponseIsNotRetried() {
        val store = FakeTokenStore(newSnapshot())
        val refreshCalls = AtomicInteger()
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = {
                refreshCalls.incrementAndGet()
                Result.success(refreshResponse())
            }
        )
        val firstUnauthorized = unauthorizedResponse(OLD_ACCESS_TOKEN)
        val secondUnauthorized = unauthorizedResponse(
            accessToken = NEW_ACCESS_TOKEN,
            priorResponse = firstUnauthorized
        )

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = secondUnauthorized
        )

        assertNull(retriedRequest)
        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun refreshEndpointIsNeverRecoveredRecursively() {
        val store = FakeTokenStore(oldSnapshot())
        val refreshCalls = AtomicInteger()
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = {
                refreshCalls.incrementAndGet()
                Result.success(refreshResponse())
            }
        )

        val retriedRequest = authenticator.authenticate(
            route = null,
            response = unauthorizedResponse(
                accessToken = OLD_ACCESS_TOKEN,
                path = "/api/v1/auth/refresh"
            )
        )

        assertNull(retriedRequest)
        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun concurrentUnauthorizedResponsesPerformSingleRefresh() {
        val store = FakeTokenStore(oldSnapshot())
        val refreshCalls = AtomicInteger()
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        val authenticator = TokenAuthenticator(
            tokenStore = store,
            refreshClient = {
                refreshCalls.incrementAndGet()
                refreshStarted.countDown()
                assertTrue(releaseRefresh.await(2, TimeUnit.SECONDS))
                Result.success(refreshResponse())
            }
        )
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)
        val start = CountDownLatch(1)

        try {
            val futures = List(CONCURRENT_REQUESTS) {
                executor.submit<Request?> {
                    start.await()
                    authenticator.authenticate(
                        route = null,
                        response = unauthorizedResponse(OLD_ACCESS_TOKEN)
                    )
                }
            }

            start.countDown()
            assertTrue(refreshStarted.await(2, TimeUnit.SECONDS))
            releaseRefresh.countDown()

            val retriedRequests = futures.map { future ->
                future.get(2, TimeUnit.SECONDS)
            }

            assertEquals(1, refreshCalls.get())
            assertEquals(newSnapshot(), store.snapshot())
            assertTrue(
                retriedRequests.all { request ->
                    request?.header("Authorization") ==
                        "Bearer $NEW_ACCESS_TOKEN"
                }
            )
        } finally {
            releaseRefresh.countDown()
            executor.shutdownNow()
        }
    }

    private fun apiFailure(
        httpCode: Int,
        code: String
    ): ApiException =
        ApiException(
            httpCode = httpCode,
            apiError = ApiErrorResponse(
                code = code,
                message = code
            )
        )

    private fun unauthorizedResponse(
        accessToken: String,
        path: String = "/api/v1/rooms",
        priorResponse: Response? = null
    ): Response {
        val request = Request.Builder()
            .url("https://example.test$path")
            .header("Authorization", "Bearer $accessToken")
            .build()

        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")

        if (priorResponse != null) {
            builder.priorResponse(priorResponse)
        }

        return builder.build()
    }

    private fun oldSnapshot(): TokenSnapshot =
        TokenSnapshot(
            accessToken = OLD_ACCESS_TOKEN,
            refreshToken = OLD_REFRESH_TOKEN
        )

    private fun newSnapshot(): TokenSnapshot =
        TokenSnapshot(
            accessToken = NEW_ACCESS_TOKEN,
            refreshToken = NEW_REFRESH_TOKEN
        )

    private fun refreshResponse(): RefreshedTokens =
        RefreshedTokens(
            accessToken = NEW_ACCESS_TOKEN,
            refreshToken = NEW_REFRESH_TOKEN
        )

    private class FakeTokenStore(
        initialSnapshot: TokenSnapshot?,
        private val snapshotFailure: Throwable? = null
    ) : TokenStore {

        private val lock = Any()
        private var storedSnapshot = initialSnapshot

        var clearCalls = 0
            private set

        override suspend fun getAccessToken(): String? =
            synchronized(lock) {
                storedSnapshot?.accessToken
            }

        override suspend fun getTokenSnapshot(): TokenSnapshot? {
            snapshotFailure?.let { throw it }

            return synchronized(lock) {
                storedSnapshot
            }
        }

        override suspend fun clearSessionIfMatches(
            expected: TokenSnapshot
        ): Boolean =
            synchronized(lock) {
                clearCalls += 1

                if (storedSnapshot == expected) {
                    storedSnapshot = null
                    true
                } else {
                    false
                }
            }

        override suspend fun updateTokens(
            accessToken: String,
            refreshToken: String
        ) {
            synchronized(lock) {
                storedSnapshot = TokenSnapshot(
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            }
        }

        fun snapshot(): TokenSnapshot? =
            synchronized(lock) {
                storedSnapshot
            }
    }

    private companion object {
        const val OLD_ACCESS_TOKEN = "access-old"
        const val OLD_REFRESH_TOKEN = "refresh-old"
        const val NEW_ACCESS_TOKEN = "access-new"
        const val NEW_REFRESH_TOKEN = "refresh-new"
        const val CONCURRENT_REQUESTS = 4
    }
}
