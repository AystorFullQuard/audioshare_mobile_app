package mme.corp.audioshare.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mme.corp.audioshare.presence.PresenceRuntimeController
import mme.corp.audioshare.room.RoomSessionRuntimeController
import mme.corp.audioshare.room.RoomSessionState
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionRuntimeGuardTest {

    @Test
    fun losingAuthenticationStopsPresenceAndClearsRoomRuntime() = runTest {
        val accessTokens = MutableStateFlow<String?>("access-token")
        val presence = FakePresenceRuntimeController()
        val rooms = FakeRoomSessionRuntimeController()

        backgroundScope.guardAuthenticatedRuntime(
            accessTokens = accessTokens,
            presenceRuntimeController = presence,
            roomSessionRuntimeController = rooms
        )
        runCurrent()

        assertEquals(0, presence.stopCalls)
        assertEquals(0, rooms.clearCalls)

        accessTokens.value = null
        runCurrent()

        assertEquals(1, presence.stopCalls)
        assertEquals(1, rooms.clearCalls)
    }

    @Test
    fun tokenRotationDoesNotClearRuntime() = runTest {
        val accessTokens = MutableStateFlow<String?>("access-old")
        val presence = FakePresenceRuntimeController()
        val rooms = FakeRoomSessionRuntimeController()

        backgroundScope.guardAuthenticatedRuntime(
            accessTokens = accessTokens,
            presenceRuntimeController = presence,
            roomSessionRuntimeController = rooms
        )
        runCurrent()

        accessTokens.value = "access-new"
        runCurrent()

        assertEquals(0, presence.stopCalls)
        assertEquals(0, rooms.clearCalls)
    }

    private class FakePresenceRuntimeController : PresenceRuntimeController {
        var stopCalls = 0
            private set

        override fun activateAfterInitialHeartbeat() = Unit

        override fun resumeAfterReconciliation() = Unit

        override fun stop() {
            stopCalls += 1
        }
    }

    private class FakeRoomSessionRuntimeController : RoomSessionRuntimeController {
        var clearCalls = 0
            private set

        override fun markDisconnected() = Unit

        override fun clearForLogout() {
            clearCalls += 1
        }

        override fun resetAfterStartupFailure() = Unit

        override suspend fun reconnect(): Result<RoomSessionState> =
            Result.success(RoomSessionState())
    }
}
