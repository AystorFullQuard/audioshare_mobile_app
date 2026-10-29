package mme.corp.audioshare.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mme.corp.audioshare.data.repository.AuthRepository
import mme.corp.audioshare.exception.ApiException

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    fun updateLogin(value: String) = mutableUiState.update {
        it.copy(login = value.take(320), error = null)
    }

    fun updatePassword(value: String) = mutableUiState.update {
        it.copy(password = value, error = null)
    }

    fun login() {
        val state = mutableUiState.value
        if (state.isLoading || state.isLoggedIn) return
        if (state.login.isBlank() || state.password.isBlank()) {
            mutableUiState.update { it.copy(error = "Enter your login and password.") }
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, error = null) }
            try {
                authRepository.login(state.login.trim(), state.password)
                    .onSuccess {
                        mutableUiState.update { current ->
                            current.copy(isLoading = false, isLoggedIn = true, password = "")
                        }
                    }
                    .onFailure { failure ->
                        mutableUiState.update { current ->
                            current.copy(
                                isLoading = false, error = when (failure) {
                                    is ApiException -> failure.apiError.displayMessage()
                                    else -> "Login failed. Check your credentials and server connection."
                                }
                            )
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Login failed. Check your credentials and server connection."
                    )
                }
            }
        }
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LoginViewModel::class.java))
            return LoginViewModel(repository) as T
        }
    }
}
