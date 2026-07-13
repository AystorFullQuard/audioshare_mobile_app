package mme.corp.audioshare.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackendSessionViewModel(
    private val repository: BackendSessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BackendSessionUiState())
    val uiState: StateFlow<BackendSessionUiState> = mutableUiState.asStateFlow()

    fun loadStoredSession() {
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                isLoading = true,
                error = null
            )

            runCatching { repository.loadStoredSession() }
                .onSuccess { session ->
                    mutableUiState.value = BackendSessionUiState(
                        isLoading = false,
                        isConnected = false,
                        userId = session.userId,
                        deviceId = session.deviceId
                    )
                }
                .onFailure { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        isLoading = false,
                        isConnected = false,
                        error = error.message ?: error::class.java.simpleName
                    )
                }
        }
    }
}
