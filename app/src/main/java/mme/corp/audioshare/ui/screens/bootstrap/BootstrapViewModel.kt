package mme.corp.audioshare.ui.screens.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.startup.BootstrapStartupCoordinator
import mme.corp.audioshare.startup.BootstrapStartupRequest

class BootstrapViewModel(
    private val startupCoordinator: BootstrapStartupCoordinator,
    private val startupRequest: BootstrapStartupRequest
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(BootstrapUiState())
    val uiState: StateFlow<BootstrapUiState> = mutableUiState.asStateFlow()

    private var startupJob: Job? = null

    init {
        initialize()
    }

    fun retry() {
        initialize()
    }

    private fun initialize() {
        if (startupJob?.isActive == true || mutableUiState.value.isComplete) {
            return
        }

        startupJob = viewModelScope.launch {
            mutableUiState.value = BootstrapUiState(
                isLoading = true,
                status = "Bootstrapping device and presence..."
            )

            try {
                startupCoordinator.initialize(startupRequest)
                    .onSuccess { snapshot ->
                        mutableUiState.value = BootstrapUiState(
                            isLoading = false,
                            isComplete = true,
                            status = "Connected as ${snapshot.presence.state.name}"
                        )
                    }
                    .onFailure { exception ->
                        mutableUiState.value = BootstrapUiState(
                            isLoading = false,
                            status = "Connection failed",
                            error = exception.toDisplayMessage()
                        )
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUiState.value = BootstrapUiState(
                    isLoading = false,
                    status = "Connection failed",
                    error = exception.toDisplayMessage()
                )
            }
        }
    }

    private fun Throwable.toDisplayMessage(): String = when (this) {
        is ApiException -> apiError.displayMessage()
        else -> message ?: "Unable to connect to ServeRelay"
    }

    class Factory(
        private val startupCoordinator: BootstrapStartupCoordinator,
        private val startupRequest: BootstrapStartupRequest
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BootstrapViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }

            return BootstrapViewModel(
                startupCoordinator = startupCoordinator,
                startupRequest = startupRequest
            ) as T
        }
    }
}
