package mme.corp.audioshare.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mme.corp.audioshare.presence.PresenceRuntimeController
import mme.corp.audioshare.room.RoomSessionRuntimeController

internal fun CoroutineScope.guardAuthenticatedRuntime(
    accessTokens: Flow<String?>,
    presenceRuntimeController: PresenceRuntimeController,
    roomSessionRuntimeController: RoomSessionRuntimeController
): Job = launch {
    accessTokens
        .distinctUntilChanged()
        .collect { accessToken ->
            if (accessToken.isNullOrBlank()) {
                presenceRuntimeController.stop()
                roomSessionRuntimeController.clearForLogout()
            }
        }
}
