package mme.corp.audioshare.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mme.corp.audioshare.data.repository.AuthRepository


class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateLogin(login: String) {
        _uiState.update {
            it.copy(login = login)
        }
    }

    fun updatePassword(password: String) {
        _uiState.update {
            it.copy(password = password)
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(error = null)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout() // If not implemented yet, we'll replace this temporarily.
            _uiState.value = LoginUiState()
        }
    }

    fun login() {

        val state = _uiState.value

        if (state.login.isBlank()) {
            _uiState.update {
                it.copy(error = "Please enter your login.")
            }
            return
        }

        if (state.password.isBlank()) {
            _uiState.update {
                it.copy(error = "Please enter your password.")
            }
            return
        }

        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null
                )
            }

            val result = authRepository.login(
                login = state.login,
                password = state.password
            )

            result.onSuccess {

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                }

            }.onFailure { throwable ->

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    class Factory(
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {

            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(authRepository) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}