package mme.corp.audioshare.presence

import mme.corp.audioshare.logging.AppLogger

class PresenceLifecycleManager(
    private val heartbeatCoordinator: PresenceHeartbeatCoordinator,
    private val logger: AppLogger = AppLogger.NO_OP
) : PresenceRuntimeController {

    private var runtimeEnabled = false
    private var appInForeground = false

    @Synchronized
    fun onAppForeground() {
        appInForeground = true

        logger.info(
            TAG,
            "Application entered foreground; runtimeEnabled=$runtimeEnabled"
        )

        if (runtimeEnabled) {
            logger.debug(
                TAG,
                "Resuming heartbeat with an immediate presence update"
            )
            heartbeatCoordinator.start(immediate = true)
        } else {
            logger.debug(
                TAG,
                "Heartbeat not started: presence runtime is not activated yet"
            )
        }
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
