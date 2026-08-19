package mme.corp.audioshare.data.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionManagerTokenStoreTest {

    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .context

        sessionManager = SessionManager(context)
        sessionManager.clearSession()
        sessionManager.saveSession(
            accessToken = OLD_ACCESS_TOKEN,
            refreshToken = OLD_REFRESH_TOKEN,
            userId = USER_ID,
            sessionId = SESSION_ID
        )
        sessionManager.saveDeviceId(DEVICE_ID)
    }

    @After
    fun tearDown() = runBlocking {
        sessionManager.clearSession()
    }

    @Test
    fun tokenSnapshotReturnsAccessAndRefreshTokenFromSameStoredSession() = runBlocking {
        val snapshot = sessionManager.getTokenSnapshot()

        assertEquals(
            TokenSnapshot(
                accessToken = OLD_ACCESS_TOKEN,
                refreshToken = OLD_REFRESH_TOKEN
            ),
            snapshot
        )
    }

    @Test
    fun updateTokensReplacesPairWithoutChangingSessionIdentity() = runBlocking {
        sessionManager.updateTokens(
            accessToken = NEW_ACCESS_TOKEN,
            refreshToken = NEW_REFRESH_TOKEN
        )

        val session = sessionManager.getSession()

        assertEquals(NEW_ACCESS_TOKEN, session.accessToken)
        assertEquals(NEW_REFRESH_TOKEN, session.refreshToken)
        assertEquals(USER_ID, session.userId)
        assertEquals(SESSION_ID, session.sessionId)
        assertEquals(DEVICE_ID, session.deviceId)
    }

    @Test
    fun matchingSessionInvalidationClearsCompleteLocalSession() =
        runBlocking {
            val cleared = sessionManager.clearSessionIfMatches(
                TokenSnapshot(
                    accessToken = OLD_ACCESS_TOKEN,
                    refreshToken = OLD_REFRESH_TOKEN
                )
            )

            val session = sessionManager.getSession()

            assertTrue(cleared)
            assertNull(session.accessToken)
            assertNull(session.refreshToken)
            assertNull(session.userId)
            assertNull(session.sessionId)
            assertNull(session.deviceId)
        }

    @Test
    fun staleSessionInvalidationDoesNotClearNewerTokenGeneration() = runBlocking {
        sessionManager.updateTokens(
            accessToken = NEW_ACCESS_TOKEN,
            refreshToken = NEW_REFRESH_TOKEN
        )

        val cleared = sessionManager.clearSessionIfMatches(
            TokenSnapshot(
                accessToken = OLD_ACCESS_TOKEN,
                refreshToken = OLD_REFRESH_TOKEN
            )
        )

        assertFalse(cleared)
        assertEquals(
            TokenSnapshot(
                accessToken = NEW_ACCESS_TOKEN,
                refreshToken = NEW_REFRESH_TOKEN
            ),
            sessionManager.getTokenSnapshot()
        )
        assertEquals(DEVICE_ID, sessionManager.getSession().deviceId)
    }

    @Test
    fun invalidTokenReplacementLeavesStoredPairUnchanged() = runBlocking {
        val blankAccessResult = runCatching {
            sessionManager.updateTokens(" ", NEW_REFRESH_TOKEN)
        }
        val blankRefreshResult = runCatching {
            sessionManager.updateTokens(NEW_ACCESS_TOKEN, " ")
        }

        assertTrue(blankAccessResult.exceptionOrNull() is IllegalArgumentException)
        assertTrue(blankRefreshResult.exceptionOrNull() is IllegalArgumentException)

        val snapshot = sessionManager.getTokenSnapshot()
        assertEquals(
            TokenSnapshot(
                accessToken = OLD_ACCESS_TOKEN,
                refreshToken = OLD_REFRESH_TOKEN
            ),
            snapshot
        )
    }

    private companion object {
        const val OLD_ACCESS_TOKEN = "access-old"
        const val OLD_REFRESH_TOKEN = "refresh-old"
        const val NEW_ACCESS_TOKEN = "access-new"
        const val NEW_REFRESH_TOKEN = "refresh-new"
        const val USER_ID = "user-id"
        const val SESSION_ID = "session-id"
        const val DEVICE_ID = "device-id"
    }
}
