package mme.corp.audioshare.presence

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mme.corp.audioshare.data.dto.presence.PresenceState
import mme.corp.audioshare.data.model.presence.PresenceSnapshot
import mme.corp.audioshare.data.repository.PresenceHeartbeatClient
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.logging.AppLogger
import kotlin.time.Duration.Companion.milliseconds

class DefaultPresenceHeartbeatCoordinator(
    private val presenceClient: PresenceHeartbeatClient,
    private val scope: CoroutineScope,
    private val heartbeatIntervalMillis: Long = DEFAULT_HEARTBEAT_INTERVAL_MILLIS,
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
    private val logger: AppLogger = AppLogger.NO_OP
) : PresenceHeartbeatCoordinator {

    private val requestMutex = Mutex()

    private val mutableConfirmedPresence =
        MutableStateFlow<PresenceSnapshot?>(null)

    override val confirmedPresence: StateFlow<PresenceSnapshot?> =
        mutableConfirmedPresence.asStateFlow()

    private val mutableDesiredState =
        MutableStateFlow<PresenceState?>(null)

    override val desiredState: StateFlow<PresenceState?> =
        mutableDesiredState.asStateFlow()

    private var heartbeatJob: Job? = null
    private var stateUpdateJob: Job? = null

    init {
        require(heartbeatIntervalMillis > 0) {
            "Heartbeat interval must be positive"
        }
        require(retryDelaysMillis.all { it >= 0 }) {
            "Retry delays must not be negative"
        }
    }

    @Synchronized
    override fun start(immediate: Boolean) {
        if (heartbeatJob?.isActive == true) {
            logger.debug(
                TAG,
                "Start ignored: heartbeat loop is already active"
            )
            return
        }

        logger.info(
            TAG,
            "Starting heartbeat loop; immediate=$immediate, " +
                "intervalMs=$heartbeatIntervalMillis"
        )

        heartbeatJob = scope.launch {
            try {
                if (immediate) {
                    val initialResult = heartbeatNow()
                    if (initialResult.isFailure &&
                        initialResult.isTerminalFailure()
                    ) {
                        logger.warn(
                            TAG,
                            "Initial heartbeat failed with a terminal error; " +
                                "heartbeat loop will stop"
                        )
                        return@launch
                    }
                }

                while (isActive) {
                    delay(heartbeatIntervalMillis.milliseconds)

                    val result = heartbeatNow()
                    if (result.isFailure && result.isTerminalFailure()) {
                        logger.warn(
                            TAG,
                            "Periodic heartbeat failed with a terminal error; " +
                                "heartbeat loop will stop"
                        )
                        break
                    }
                }
            } finally {
                logger.debug(TAG, "Heartbeat loop finished")
            }
        }
    }

    @Synchronized
    override fun stop() {
        val hadActiveWork =
            heartbeatJob?.isActive == true ||
                stateUpdateJob?.isActive == true

        if (hadActiveWork) {
            logger.info(TAG, "Stopping heartbeat runtime")
        } else {
            logger.debug(TAG, "Stop ignored: heartbeat runtime is not active")
        }

        stateUpdateJob?.cancel()
        stateUpdateJob = null

        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    @Synchronized
    override fun setDesiredState(state: PresenceState?) {
        val previousState = mutableDesiredState.value
        mutableDesiredState.value = state

        logger.info(
            TAG,
            "Desired state changed: " +
                "${previousState.toLogValue()} -> ${state.toLogValue()}"
        )

        if (heartbeatJob?.isActive != true) {
            logger.debug(
                TAG,
                "Desired state stored; heartbeat loop is not active"
            )
            return
        }

        stateUpdateJob?.cancel()
        stateUpdateJob = scope.launch {
            logger.debug(
                TAG,
                "Sending immediate heartbeat after desired state change"
            )

            val result = heartbeatNow()
            if (result.isFailure && result.isTerminalFailure()) {
                logger.warn(
                    TAG,
                    "Desired state heartbeat failed with a terminal error; " +
                        "stopping heartbeat runtime"
                )
                stop()
            }
        }
    }

    override suspend fun heartbeatNow(): Result<PresenceSnapshot> =
        requestMutex.withLock {
            heartbeatWithRetry()
        }

    private suspend fun heartbeatWithRetry(): Result<PresenceSnapshot> {
        var retryIndex = 0

        while (true) {
            val requestedState = mutableDesiredState.value

            logger.debug(
                TAG,
                "Sending heartbeat; desiredState=${requestedState.toLogValue()}, " +
                    "attempt=${retryIndex + 1}"
            )

            val result = try {
                presenceClient.heartbeat(requestedState)
            } catch (exception: CancellationException) {
                logger.debug(TAG, "Heartbeat request cancelled")
                throw exception
            } catch (exception: Exception) {
                logger.error(
                    TAG,
                    "Heartbeat client threw ${exception.safeTypeName()}",
                    exception
                )
                Result.failure(exception)
            }

            result.onSuccess { snapshot ->
                mutableConfirmedPresence.value = snapshot

                logger.debug(
                    TAG,
                    "Heartbeat confirmed; state=${snapshot.state.name}, " +
                        "hasCurrentRoom=${snapshot.currentRoomId != null}"
                )
            }

            val failure = result.exceptionOrNull() ?: return result

            if (!failure.isRetryable() ||
                retryIndex >= retryDelaysMillis.size
            ) {
                if (failure is ApiException &&
                    failure.apiError.code == PRESENCE_STATE_INVALID
                ) {
                    mutableDesiredState.value = null

                    logger.warn(
                        TAG,
                        "Server rejected desired presence state; " +
                            "desired state cleared"
                    )
                }

                logTerminalFailure(failure)
                return result
            }

            val retryDelayMillis = retryDelaysMillis[retryIndex]

            logRetryableFailure(
                failure = failure,
                retryNumber = retryIndex + 1,
                retryDelayMillis = retryDelayMillis
            )

            delay(retryDelayMillis.milliseconds)
            retryIndex += 1
        }
    }

    private fun logRetryableFailure(
        failure: Throwable,
        retryNumber: Int,
        retryDelayMillis: Long
    ) {
        val message =
            "Heartbeat failed; ${failure.safeSummary()}, " +
                "retry=$retryNumber, delayMs=$retryDelayMillis"

        if (failure is ApiException) {
            logger.warn(TAG, message)
        } else {
            logger.warn(TAG, message, failure)
        }
    }

    private fun logTerminalFailure(failure: Throwable) {
        val message =
            "Heartbeat failed without further retry; ${failure.safeSummary()}"

        if (failure is ApiException) {
            logger.error(TAG, message)
        } else {
            logger.error(TAG, message, failure)
        }
    }

    private fun Result<PresenceSnapshot>.isTerminalFailure(): Boolean {
        val failure = exceptionOrNull() ?: return false
        return !failure.isRetryable()
    }

    private fun Throwable.isRetryable(): Boolean =
        this is IOException ||
            (this is ApiException && httpCode in RETRYABLE_HTTP_CODES)

    private fun Throwable.safeSummary(): String =
        if (this is ApiException) {
            "type=ApiException, httpCode=$httpCode, " +
                "apiCode=${apiError.code}"
        } else {
            "type=${safeTypeName()}"
        }

    private fun Throwable.safeTypeName(): String =
        this::class.java.simpleName.ifBlank { "Throwable" }

    private fun PresenceState?.toLogValue(): String =
        this?.name ?: KEEP_CURRENT_STATE

    private companion object {
        const val TAG = "PresenceHeartbeat"
        const val KEEP_CURRENT_STATE = "KEEP_CURRENT"
        const val DEFAULT_HEARTBEAT_INTERVAL_MILLIS = 30_000L
        const val PRESENCE_STATE_INVALID = "PRESENCE_STATE_INVALID"

        val DEFAULT_RETRY_DELAYS_MILLIS = listOf(
            2_000L,
            5_000L,
            10_000L,
            30_000L
        )

        val RETRYABLE_HTTP_CODES = setOf(502, 503, 504)
    }
}
