package mme.corp.audioshare.presence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot

data class PresenceHeartbeatFailure(
    val type: String,
    val httpCode: Int? = null,
    val apiCode: String? = null
)

interface PresenceHeartbeatCoordinator {

    val confirmedPresence: StateFlow<PresenceSnapshot?>

    val desiredState: StateFlow<PresenceState?>

    val terminalFailures: Flow<PresenceHeartbeatFailure>

    fun start(immediate: Boolean = true)

    fun stop()

    fun setDesiredState(state: PresenceState?)

    /**
     * Sends a caller-owned one-shot heartbeat. Terminal failures are returned
     * to the caller and are not re-emitted through [terminalFailures].
     */
    suspend fun heartbeatNow(): Result<PresenceSnapshot>
}
