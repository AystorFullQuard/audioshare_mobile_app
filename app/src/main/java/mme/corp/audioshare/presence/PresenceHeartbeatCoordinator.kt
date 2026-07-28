package mme.corp.audioshare.presence

import kotlinx.coroutines.flow.StateFlow
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot

interface PresenceHeartbeatCoordinator {

    val confirmedPresence: StateFlow<PresenceSnapshot?>

    val desiredState: StateFlow<PresenceState?>

    fun start(immediate: Boolean = true)

    fun stop()

    fun setDesiredState(state: PresenceState?)

    suspend fun heartbeatNow(): Result<PresenceSnapshot>
}
