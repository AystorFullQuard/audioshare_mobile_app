package mme.corp.audioshare.presence

import mme.corp.audioshare.logging.AppLogger

class PresenceLifecycleManager(
    private val heartbeatCoordinator: PresenceHeartbeatCoordinator,
    private val logger: AppLogger = AppLogger.NO_OP
) : PresenceRuntimeController {

    private var runtimeEnabled = false
    private var appInForeground = false

    /**
     * Marks the process as foreground. Returns true only when startup already
     * activated the Presence runtime and foreground reconciliation may run.
     */
    @Synchronized
    fun onAppForeground(): Boolean {
        appInForeground = true

        logger.info(
            TAG,
            "Application entered foreground; runtimeEnabled=$runtimeEnabled"
        )

        if (runtimeEnabled) {
            logger.debug(
                TAG,
                "Presence runtime awaits foreground reconciliation"
            )
        } else {
            logger.debug(
                TAG,
                "Foreground reconciliation deferred: presence runtime is not activated yet"
            )
        }

        return runtimeEnabled
    }

    @Synchronized
    fun onAppBackground() {
        appInForeground = false

        logger.info(
            TAG,
            "Application entered background; stopping heartbeat"
        )

        heartbeatCoordinator.stop()
    }

    @Synchronized
    override fun activateAfterInitialHeartbeat() {
        runtimeEnabled = true

        logger.info(
            TAG,
            "Presence runtime activated; appInForeground=$appInForeground"
        )

        if (appInForeground) {
            // Startup already sent and awaited the first heartbeat.
            logger.debug(
                TAG,
                "Starting periodic heartbeat without another immediate request"
            )
            heartbeatCoordinator.start(immediate = false)
        }
    }

    @Synchronized
    override fun resumeAfterReconciliation() {
        if (!runtimeEnabled || !appInForeground) {
            logger.debug(
                TAG,
                "Heartbeat resume skipped; runtimeEnabled=$runtimeEnabled, " +
                    "appInForeground=$appInForeground"
            )
            return
        }

        logger.debug(
            TAG,
            "Foreground reconciliation completed; resuming periodic heartbeat"
        )
        heartbeatCoordinator.start(immediate = false)
    }

    @Synchronized
    override fun stop() {
        val wasEnabled = runtimeEnabled
        runtimeEnabled = false

        logger.info(
            TAG,
            "Presence runtime stopped; wasEnabled=$wasEnabled"
        )

        heartbeatCoordinator.stop()
    }

    private companion object {
        const val TAG = "PresenceLifecycle"
    }
}
