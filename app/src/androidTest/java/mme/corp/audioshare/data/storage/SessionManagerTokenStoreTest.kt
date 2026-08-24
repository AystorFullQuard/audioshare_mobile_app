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
        prepareUser(
            userId = USER_ID,
            sessionId = SESSION_ID
        )
        sessionManager.clearDeviceId()
        sessionManager.saveDeviceId(DEVICE_ID)
    }

    @After
    fun tearDown() = runBlocking {
        clearUserDevice(USER_ID, SESSION_ID)
        clearUserDevice(SECOND_USER_ID, SECOND_SESSION_ID)
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
    fun clearSessionPreservesDeviceIdentityForSameUserRelogin() = runBlocking {
        sessionManager.clearSession()

        val loggedOutSession = sessionManager.getSession()
        assertNull(loggedOutSession.accessToken)
        assertNull(loggedOutSession.refreshToken)
        assertNull(loggedOutSession.userId)
        assertNull(loggedOutSession.sessionId)
        assertNull(loggedOutSession.deviceId)

        prepareUser(
            userId = USER_ID,
            sessionId = "session-id-after-login"
        )

        assertEquals(DEVICE_ID, sessionManager.getDeviceId())
        assertEquals(DEVICE_ID, sessionManager.getSession().deviceId)
    }

    @Test
    fun matchingSessionInvalidationClearsAuthButPreservesDeviceIdentity() = runBlocking {
        val cleared = sessionManager.clearSessionIfMatches(
            TokenSnapshot(
                accessToken = OLD_ACCESS_TOKEN,
                refreshToken = OLD_REFRESH_TOKEN
            )
        )

        val clearedSession = sessionManager.getSession()

        assertTrue(cleared)
        assertNull(clearedSession.accessToken)
        assertNull(clearedSession.refreshToken)
        assertNull(clearedSession.userId)
        assertNull(clearedSession.sessionId)
        assertNull(clearedSession.deviceId)

        prepareUser(
            userId = USER_ID,
            sessionId = "session-id-after-recovery"
        )

        assertEquals(DEVICE_ID, sessionManager.getDeviceId())
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
    fun deviceIdentityIsScopedToAuthenticatedUser() = runBlocking {
        sessionManager.clearSession()
        prepareUser(SECOND_USER_ID, SECOND_SESSION_ID)
        sessionManager.clearDeviceId()
        sessionManager.saveDeviceId(SECOND_DEVICE_ID)

        sessionManager.clearSession()
        prepareUser(USER_ID, SESSION_ID)
        assertEquals(DEVICE_ID, sessionManager.getDeviceId())

        sessionManager.clearSession()
        prepareUser(SECOND_USER_ID, SECOND_SESSION_ID)
        assertEquals(SECOND_DEVICE_ID, sessionManager.getDeviceId())
    }

    @Test
    fun clearDeviceIdOnlyInvalidatesCurrentUsersMapping() = runBlocking {
        sessionManager.clearSession()
        prepareUser(SECOND_USER_ID, SECOND_SESSION_ID)
        sessionManager.clearDeviceId()
        sessionManager.saveDeviceId(SECOND_DEVICE_ID)
        sessionManager.clearDeviceId()

        assertNull(sessionManager.getDeviceId())

        sessionManager.clearSession()
        prepareUser(USER_ID, SESSION_ID)
        assertEquals(DEVICE_ID, sessionManager.getDeviceId())
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

    private suspend fun prepareUser(
        userId: String,
        sessionId: String
    ) {
        sessionManager.clearSession()
        sessionManager.saveSession(
            accessToken = OLD_ACCESS_TOKEN,
            refreshToken = OLD_REFRESH_TOKEN,
            userId = userId,
            sessionId = sessionId
        )
    }

    private suspend fun clearUserDevice(
        userId: String,
        sessionId: String
    ) {
        prepareUser(userId, sessionId)
        sessionManager.clearDeviceId()
    }

    private companion object {
        const val OLD_ACCESS_TOKEN = "access-old"
        const val OLD_REFRESH_TOKEN = "refresh-old"
        const val NEW_ACCESS_TOKEN = "access-new"
        const val NEW_REFRESH_TOKEN = "refresh-new"
        const val USER_ID = "user-id"
        const val SESSION_ID = "session-id"
        const val DEVICE_ID = "device-id"
        const val SECOND_USER_ID = "user-id-two"
        const val SECOND_SESSION_ID = "session-id-two"
        const val SECOND_DEVICE_ID = "device-id-two"
    }
}
