package mme.corp.audioshare.presence

import mme.corp.audioshare.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceHeartbeatBuildConfigTest {

    @Test
    fun heartbeatIntervalMatchesBuildTypePolicy() {
        val expectedIntervalMillis =
            if (BuildConfig.DEBUG) DEBUG_HEARTBEAT_INTERVAL_MILLIS
            else RELEASE_HEARTBEAT_INTERVAL_MILLIS

        assertEquals(
            expectedIntervalMillis,
            BuildConfig.PRESENCE_HEARTBEAT_INTERVAL_MILLIS
        )
    }

    @Test
    fun heartbeatIntervalIsPositive() {
        assertTrue(BuildConfig.PRESENCE_HEARTBEAT_INTERVAL_MILLIS > 0L)
    }

    private companion object {
        const val DEBUG_HEARTBEAT_INTERVAL_MILLIS = 5_000L
        const val RELEASE_HEARTBEAT_INTERVAL_MILLIS = 30_000L
    }
}
