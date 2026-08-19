package mme.corp.audioshare.ui.screens.rooms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MemberPresencePollingTest {

    private val config = MemberPresencePollingConfig(
        intervalMillis = 5_000L,
        maxBackoffMillis = 60_000L
    )

    @Test
    fun retryableFailuresBackOffExponentiallyAndCapAtMaximum() {
        var delay = config.intervalMillis

        delay = nextMemberPresencePollDelayMillis(
            delay,
            MemberPresencePollResult.RETRYABLE_FAILURE,
            config
        )
        assertEquals(10_000L, delay)

        delay = nextMemberPresencePollDelayMillis(
            delay,
            MemberPresencePollResult.RETRYABLE_FAILURE,
            config
        )
        assertEquals(20_000L, delay)

        delay = nextMemberPresencePollDelayMillis(
            40_000L,
            MemberPresencePollResult.RETRYABLE_FAILURE,
            config
        )
        assertEquals(60_000L, delay)

        delay = nextMemberPresencePollDelayMillis(
            delay,
            MemberPresencePollResult.RETRYABLE_FAILURE,
            config
        )
        assertEquals(60_000L, delay)
    }

    @Test
    fun successAndNonRetryableOutcomesResetToBaseInterval() {
        listOf(
            MemberPresencePollResult.SUCCESS,
            MemberPresencePollResult.SKIPPED,
            MemberPresencePollResult.FAILURE,
            MemberPresencePollResult.STOP
        ).forEach { result ->
            assertEquals(
                config.intervalMillis,
                nextMemberPresencePollDelayMillis(
                    currentDelayMillis = 40_000L,
                    result = result,
                    config = config
                )
            )
        }
    }

    @Test
    fun pollingConfigRejectsInvalidIntervals() {
        assertThrows(IllegalArgumentException::class.java) {
            MemberPresencePollingConfig(0L, 60_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MemberPresencePollingConfig(5_000L, 4_999L)
        }
    }
}
