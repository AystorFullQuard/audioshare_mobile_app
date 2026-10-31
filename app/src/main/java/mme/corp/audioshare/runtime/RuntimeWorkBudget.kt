package mme.corp.audioshare.runtime

/** Small, deterministic limits for lifecycle/network work: never busy-loop or grow unbounded. */
data class RuntimeWorkBudget(
    val foregroundPollingMillis: Long = 15_000,
    val backgroundPollingMillis: Long = 60_000,
    val maxBackoffMillis: Long = 120_000,
    val maxPendingEvents: Int = 32,
    val maxDiscoveryMillis: Long = 5_000
) {
    init {
        require(foregroundPollingMillis in 1_000..120_000)
        require(backgroundPollingMillis >= foregroundPollingMillis)
        require(maxBackoffMillis >= backgroundPollingMillis)
        require(maxPendingEvents in 1..256)
        require(maxDiscoveryMillis in 250..10_000)
    }

    fun nextDelay(retry: Int, inForeground: Boolean): Long {
        val baseline = if (inForeground) foregroundPollingMillis else backgroundPollingMillis
        val steps = retry.coerceIn(0, 10)
        var delay = baseline
        repeat(steps) { delay = if (delay > maxBackoffMillis / 2) maxBackoffMillis else delay * 2 }
        return delay.coerceAtMost(maxBackoffMillis)
    }
}
