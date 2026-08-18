package mme.corp.audioshare.presence

interface PresenceRuntimeController {

    fun activateAfterInitialHeartbeat()

    fun resumeAfterReconciliation()

    fun stop()

    companion object {
        val NO_OP: PresenceRuntimeController = object : PresenceRuntimeController {
            override fun activateAfterInitialHeartbeat() = Unit
            override fun resumeAfterReconciliation() = Unit
            override fun stop() = Unit
        }
    }
}
