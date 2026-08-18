package mme.corp.audioshare.ui.screens.rooms

internal data class MemberPresencePollingConfig(
    val intervalMillis: Long,
    val maxBackoffMillis: Long
) {
    init {
        require(intervalMillis > 0) { "Polling interval must be positive." }
        require(maxBackoffMillis >= intervalMillis) {
            "Polling max backoff must be at least the base interval."
        }
    }
}

internal enum class MemberPresencePollResult {
    SUCCESS,
    SKIPPED,
    RETRYABLE_FAILURE,
    FAILURE,
    STOP
}

internal fun nextMemberPresencePollDelayMillis(
    currentDelayMillis: Long,
    result: MemberPresencePollResult,
    config: MemberPresencePollingConfig
): Long = when (result) {
    MemberPresencePollResult.RETRYABLE_FAILURE -> {
        val cappedCurrent = currentDelayMillis.coerceAtLeast(config.intervalMillis)
        if (cappedCurrent >= config.maxBackoffMillis) {
            config.maxBackoffMillis
        } else if (cappedCurrent > config.maxBackoffMillis / 2) {
            config.maxBackoffMillis
        } else {
            cappedCurrent * 2
        }
    }

    MemberPresencePollResult.SUCCESS,
    MemberPresencePollResult.SKIPPED,
    MemberPresencePollResult.FAILURE,
    MemberPresencePollResult.STOP -> config.intervalMillis
}
