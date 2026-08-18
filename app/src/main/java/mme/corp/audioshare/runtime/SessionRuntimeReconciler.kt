package mme.corp.audioshare.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.logging.AppLogger
import mme.corp.audioshare.presence.PresenceHeartbeatCoordinator
import mme.corp.audioshare.presence.PresenceHeartbeatFailure
import mme.corp.audioshare.presence.PresenceRuntimeController
import mme.corp.audioshare.room.RoomSessionCoordinator
import mme.corp.audioshare.room.RoomSessionState
import java.io.IOException

class SessionRuntimeReconciler(
    private val roomSessionCoordinator: RoomSessionCoordinator,
    private val heartbeatCoordinator: PresenceHeartbeatCoordinator,
    private val presenceRuntimeController: PresenceRuntimeController,
    private val scope: CoroutineScope,
    private val logger: AppLogger = AppLogger.NO_OP
) {

    private val jobLock = Any()
    private var appInForeground = false
    private var reconciliationJob: Job? = null

    init {
        scope.launch {
            heartbeatCoordinator.terminalFailures.collect { failure ->
                handleTerminalHeartbeatFailure(failure)
            }
        }
    }

    fun onAppForeground(runtimeReady: Boolean) {
        synchronized(jobLock) {
            appInForeground = true
        }

        if (runtimeReady) {
            requestReconciliation(ReconciliationReason.FOREGROUND)
        }
    }

    fun onAppBackground() {
        val job = synchronized(jobLock) {
            appInForeground = false
            reconciliationJob.also { reconciliationJob = null }
        }
        job?.cancel()
    }

    private fun handleTerminalHeartbeatFailure(
        failure: PresenceHeartbeatFailure
    ) {
        if (failure.apiCode != PRESENCE_STATE_INVALID) {
            logger.warn(
                TAG,
                "Terminal heartbeat failure is not room-reconcilable; " +
                    "type=${failure.type}, httpCode=${failure.httpCode}, " +
                    "apiCode=${failure.apiCode ?: NONE}"
            )
            return
        }

        requestReconciliation(ReconciliationReason.TERMINAL_HEARTBEAT)
    }

    private fun requestReconciliation(reason: ReconciliationReason) {
        val job = synchronized(jobLock) {
            if (!appInForeground || reconciliationJob?.isActive == true) {
                logger.debug(
                    TAG,
                    "Reconciliation request ignored; reason=${reason.name}, " +
                        "appInForeground=$appInForeground, " +
                        "alreadyRunning=${reconciliationJob?.isActive == true}"
                )
                return
            }

            scope.launch {
                runReconciliation(reason)
            }.also { reconciliationJob = it }
        }

        job.invokeOnCompletion {
            synchronized(jobLock) {
                if (reconciliationJob === job) {
                    reconciliationJob = null
                }
            }
        }
    }

    private suspend fun runReconciliation(reason: ReconciliationReason) {
        logger.info(
            TAG,
            "Session runtime reconciliation started; reason=${reason.name}, " +
                "hasCurrentRoom=${roomSessionCoordinator.state.value.currentRoom != null}"
        )

        val shouldResumeHeartbeat = try {
            reconcileForegroundState(reason)
        } catch (exception: CancellationException) {
            logger.debug(
                TAG,
                "Session runtime reconciliation cancelled; reason=${reason.name}"
            )
            throw exception
        } catch (exception: Exception) {
            logger.error(
                TAG,
                "Session runtime reconciliation failed unexpectedly; " +
                    "reason=${reason.name}, type=${exception.safeTypeName()}",
                exception
            )
            false
        }

        if (shouldResumeHeartbeat) {
            presenceRuntimeController.resumeAfterReconciliation()
        }
    }

    private suspend fun reconcileForegroundState(
        reason: ReconciliationReason
    ): Boolean {
        val expectedRoomId = roomSessionCoordinator.state.value.currentRoom?.id
        if (reason == ReconciliationReason.TERMINAL_HEARTBEAT) {
            return recoverRejectedPresence(expectedRoomId)
        }

        val heartbeatResult = heartbeatCoordinator.heartbeatNow()
        val snapshot = heartbeatResult.getOrElse { exception ->
            if (exception.isPresenceStateInvalid()) {
                logger.warn(
                    TAG,
                    "Presence heartbeat rejected active-room context; " +
                        "reconciling room session"
                )
                return recoverRejectedPresence(expectedRoomId)
            }

            logHeartbeatFailure(reason, exception)
            return exception.isRetryable()
        }

        if (snapshot.disagreesWith(expectedRoomId)) {
            logger.warn(
                TAG,
                "Presence heartbeat disagreed with local active-room context; " +
                    "hasLocalRoom=${expectedRoomId != null}, " +
                    "hasServerRoom=${snapshot.currentRoomId != null}"
            )
            return recoverRejectedPresence(expectedRoomId)
        }

        if (expectedRoomId == null) {
            heartbeatCoordinator.setDesiredState(PresenceState.ONLINE)
            logger.info(TAG, "Foreground reconciliation completed without active room")
            return true
        }

        return refreshActiveRoom(expectedRoomId)
    }

    private suspend fun refreshActiveRoom(expectedRoomId: String): Boolean {
        val roomResult = runRoomOperationWhenIdle {
            roomSessionCoordinator.refreshCurrentRoom()
        }
        if (roomResult.isFailure) {
            logRoomFailure("room refresh", roomResult.exceptionOrNull())
            return true
        }

        val roomAfterRefresh = roomSessionCoordinator.state.value.currentRoom
        if (roomAfterRefresh?.id != expectedRoomId) {
            return synchronizeClearedRoomContext(expectedRoomId)
        }

        val membersResult = runRoomOperationWhenIdle {
            roomSessionCoordinator.refreshActiveMembers()
        }
        if (membersResult.isFailure) {
            logRoomFailure("member refresh", membersResult.exceptionOrNull())
            return true
        }

        if (roomSessionCoordinator.state.value.currentRoom?.id != expectedRoomId) {
            return synchronizeClearedRoomContext(expectedRoomId)
        }

        logger.info(
            TAG,
            "Foreground active room reconciled; hasCurrentRoom=true"
        )
        return true
    }

    private suspend fun recoverRejectedPresence(
        expectedRoomId: String?
    ): Boolean {
        val reconnectResult = runRoomOperationWhenIdle {
            roomSessionCoordinator.reconnect()
        }
        val reconnected = reconnectResult.getOrElse { exception ->
            logRoomFailure("room reconnect", exception)
            return false
        }

        val currentRoom = reconnected.currentRoom
        if (currentRoom == null) {
            heartbeatCoordinator.setDesiredState(PresenceState.ONLINE)
            return heartbeatAfterReconciliation(expectedRoomId = null)
        }

        if (expectedRoomId != null && currentRoom.id != expectedRoomId) {
            logger.info(
                TAG,
                "Active room changed during reconciliation; preserving newer context"
            )
            return true
        }

        val activateResult = runRoomOperationWhenIdle {
            roomSessionCoordinator.activateRoom(currentRoom.id)
        }
        if (activateResult.isFailure) {
            logRoomFailure("room reactivation", activateResult.exceptionOrNull())
            return false
        }

        return heartbeatAfterReconciliation(expectedRoomId = currentRoom.id)
    }

    private suspend fun synchronizeClearedRoomContext(
        previousRoomId: String
    ): Boolean {
        val currentRoomId = roomSessionCoordinator.state.value.currentRoom?.id
        if (currentRoomId != null && currentRoomId != previousRoomId) {
            logger.info(
                TAG,
                "Active room changed during refresh; preserving newer context"
            )
            return true
        }

        heartbeatCoordinator.setDesiredState(PresenceState.ONLINE)
        return heartbeatAfterReconciliation(expectedRoomId = null)
    }

    private suspend fun heartbeatAfterReconciliation(
        expectedRoomId: String?
    ): Boolean {
        val result = heartbeatCoordinator.heartbeatNow()
        val snapshot = result.getOrElse { exception ->
            logHeartbeatFailure(ReconciliationReason.RECOVERY, exception)
            return exception.isRetryable()
        }

        val matches = !snapshot.disagreesWith(expectedRoomId)
        if (!matches) {
            logger.warn(
                TAG,
                "Reconciled heartbeat still disagrees with room context; " +
                    "hasExpectedRoom=${expectedRoomId != null}, " +
                    "hasServerRoom=${snapshot.currentRoomId != null}"
            )
        }
        return matches
    }

    private suspend fun runRoomOperationWhenIdle(
        operation: suspend () -> Result<RoomSessionState>
    ): Result<RoomSessionState> {
        repeat(MAX_ROOM_OPERATION_ATTEMPTS) {
            roomSessionCoordinator.state.first { !it.isBusy }
            val result = operation()
            if (result.isSuccess || !roomSessionCoordinator.state.value.isBusy) {
                return result
            }
        }

        return Result.failure(
            IllegalStateException("Room session remained busy during reconciliation")
        )
    }

    private fun PresenceSnapshot.disagreesWith(expectedRoomId: String?): Boolean =
        currentRoomId != expectedRoomId

    private fun Throwable.isPresenceStateInvalid(): Boolean =
        this is ApiException && apiError.code == PRESENCE_STATE_INVALID

    private fun Throwable.isRetryable(): Boolean =
        this is IOException ||
            (this is ApiException && httpCode in RETRYABLE_HTTP_CODES)

    private fun logHeartbeatFailure(
        reason: ReconciliationReason,
        exception: Throwable
    ) {
        if (exception is ApiException) {
            logger.warn(
                TAG,
                "Heartbeat failed during reconciliation; reason=${reason.name}, " +
                    "httpCode=${exception.httpCode}, apiCode=${exception.apiError.code}"
            )
        } else {
            logger.warn(
                TAG,
                "Heartbeat failed during reconciliation; reason=${reason.name}, " +
                    "type=${exception.safeTypeName()}"
            )
        }
    }

    private fun logRoomFailure(stage: String, exception: Throwable?) {
        if (exception is ApiException) {
            logger.warn(
                TAG,
                "Foreground $stage failed; httpCode=${exception.httpCode}, " +
                    "apiCode=${exception.apiError.code}"
            )
        } else {
            logger.warn(
                TAG,
                "Foreground $stage failed; type=${exception.safeTypeName()}"
            )
        }
    }

    private fun Throwable?.safeTypeName(): String =
        this?.javaClass?.simpleName?.ifBlank { "Throwable" } ?: NONE

    private enum class ReconciliationReason {
        FOREGROUND,
        TERMINAL_HEARTBEAT,
        RECOVERY
    }

    private companion object {
        const val TAG = "SessionRuntime"
        const val NONE = "NONE"
        const val PRESENCE_STATE_INVALID = "PRESENCE_STATE_INVALID"
        const val MAX_ROOM_OPERATION_ATTEMPTS = 3

        val RETRYABLE_HTTP_CODES = setOf(502, 503, 504)
    }
}
