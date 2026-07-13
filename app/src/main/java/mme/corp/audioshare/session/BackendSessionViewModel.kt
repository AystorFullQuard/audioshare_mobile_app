package mme.corp.audioshare.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mme.corp.audioshare.backend.ApiResult

class BackendSessionViewModel(
    private val repository: BackendSessionRepository,
    private val startupRequest: BackendSessionStartupRequest
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BackendSessionUiState())
    val uiState: StateFlow<BackendSessionUiState> = mutableUiState.asStateFlow()
    private val initializationStarted = AtomicBoolean(false)

    init {
        initialize()
    }

    fun retry() {
        initialize(force = true)
    }

    private fun initialize(force: Boolean = false) {
        if (!force && !initializationStarted.compareAndSet(false, true)) {
            return
        }
        if (mutableUiState.value.isLoading) {
            return
        }

        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                isLoading = true,
                isConnected = false,
                status = "Connecting to ServeRelay...",
                error = null
            )

            try {
                when (val result = repository.initializeSession(startupRequest)) {
                    is BackendSessionStartupResult.Success -> {
                        val snapshot = result.snapshot
                        mutableUiState.value = BackendSessionUiState(
                            isLoading = false,
                            isConnected = true,
                            status = "Connected",
                            userId = snapshot.userId,
                            deviceId = snapshot.deviceId,
                            deviceName = snapshot.deviceName,
                            lastHeartbeatAt = snapshot.lastHeartbeatAt,
                            deviceCount = snapshot.deviceCount
                        )
                    }

                    is BackendSessionStartupResult.Failure -> {
                        mutableUiState.value = mutableUiState.value.copy(
                            isLoading = false,
                            isConnected = false,
                            status = "Connection failed at ${result.step.name.lowercase()}",
                            error = result.apiFailure.toDisplayMessage()
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false,
                    isConnected = false,
                    status = "Connection failed",
                    error = exception.message ?: exception::class.java.simpleName
                )
            }
        }
    }

    private fun ApiResult.Failure.toDisplayMessage(): String {
        val backendCode = error?.code
        val backendMessage = error?.message
        val exceptionMessage = cause?.message

        return listOfNotNull(
            backendCode,
            backendMessage,
            exceptionMessage
        ).distinct().joinToString(": ").ifBlank {
            statusCode?.let { "HTTP $it" } ?: "Unknown backend error"
        }
    }

    companion object {
        fun factory(
            repository: BackendSessionRepository,
            startupRequest: BackendSessionStartupRequest
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(BackendSessionViewModel::class.java))
                return BackendSessionViewModel(repository, startupRequest) as T
            }
        }
    }
}
