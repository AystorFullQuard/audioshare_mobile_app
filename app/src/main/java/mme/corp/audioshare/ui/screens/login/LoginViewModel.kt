package mme.corp.audioshare.ui.screens.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mme.corp.audioshare.data.repository.AuthRepository
import mme.corp.audioshare.exception.ApiException

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val TAG = "LoginVM"
    }

    private val _uiState = MutableStateFlow(LoginUiState())

    val uiState: StateFlow<LoginUiState> =
        _uiState.asStateFlow()

    init {
        Log.d(TAG, "==========================================")
        Log.d(TAG, "LoginViewModel created")
        Log.d(TAG, "Initial state = ${_uiState.value}")
    }

    fun updateLogin(login: String) {

        Log.d(TAG, "updateLogin()")
        Log.d(TAG, "New login = '$login'")

        _uiState.update {
            it.copy(login = login)
        }

        Log.d(TAG, "Login updated")
    }

    fun updatePassword(password: String) {

        Log.d(TAG, "updatePassword()")
        Log.d(TAG, "Password length = ${password.length}")

        _uiState.update {
            it.copy(password = password)
        }

        Log.d(TAG, "Password updated")
    }

    fun clearError() {

        Log.d(TAG, "clearError()")

        _uiState.update {
            it.copy(error = null)
        }
    }

    fun logout() {

        Log.d(TAG, "==========================================")
        Log.d(TAG, "Logout requested")

        viewModelScope.launch {

            try {

                Log.d(TAG, "Calling AuthRepository.logout()")

                val result = authRepository.logout()

                Log.d(TAG, "Logout result = $result")

                _uiState.value = LoginUiState()

                Log.d(TAG, "UI reset after logout")

            } catch (e: Exception) {

                Log.e(TAG, "Logout crashed", e)

                _uiState.update {
                    it.copy(error = e.message ?: "Logout failed")
                }
            }
        }
    }

    fun login() {

        val state = _uiState.value

        Log.d(TAG, "==========================================")
        Log.d(TAG, "LOGIN START")
        Log.d(TAG, "Username = '${state.login}'")
        Log.d(TAG, "Password length = ${state.password.length}")
        Log.d(TAG, "Current UI state = $state")

        if (state.login.isBlank()) {

            Log.w(TAG, "Validation failed: login is empty")

            _uiState.update {
                it.copy(error = "Please enter your login.")
            }

            return
        }

        if (state.password.isBlank()) {

            Log.w(TAG, "Validation failed: password is empty")

            _uiState.update {
                it.copy(error = "Please enter your password.")
            }

            return
        }

        viewModelScope.launch {

            try {

                Log.d(TAG, "Setting loading=true")

                _uiState.update {
                    it.copy(
                        isLoading = true,
                        error = null
                    )
                }

                Log.d(TAG, "Calling AuthRepository.login()")

                val result = authRepository.login(
                    login = state.login,
                    password = state.password
                )

                Log.d(TAG, "Repository returned")
                Log.d(TAG, "Result = $result")

                result.onSuccess { response ->

                    Log.i(TAG, "LOGIN SUCCESS")
                    Log.d(TAG, "UserId = ${response.userId}")
                    Log.d(TAG, "SessionId = ${response.sessionId}")
                    Log.d(TAG, "Token type = ${response.tokenType}")

                    Log.d(TAG, "Updating UI state")

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true
                        )
                    }

                    Log.i(TAG, "UI updated successfully")
                    Log.i(TAG, "isLoggedIn = true")
                }

                result.onFailure { throwable ->

                    Log.e(TAG, "==========================================")
                    Log.e(TAG, "LOGIN FAILED", throwable)
                    Log.e(TAG, "Exception = ${throwable::class.simpleName}")

                    val errorMessage = when (throwable) {

                        is ApiException -> {

                            Log.e(TAG, throwable.apiError.debugString())

                            throwable.apiError.displayMessage()
                        }

                        else -> {

                            Log.e(TAG, "Message = ${throwable.message}")

                            throwable.message ?: "Unknown error"
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = errorMessage
                        )
                    }

                    Log.d(TAG, "UI updated with error")
                }

            } catch (e: Exception) {

                Log.e(TAG, "==========================================")
                Log.e(TAG, "UNEXPECTED EXCEPTION INSIDE LOGIN()", e)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unexpected error"
                    )
                }

                Log.d(TAG, "Recovered from exception")
            } finally {

                Log.d(TAG, "LOGIN FLOW FINISHED")
                Log.d(TAG, "Final UI State = ${_uiState.value}")
                Log.d(TAG, "==========================================")
            }
        }
    }

    class Factory(
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {

            Log.d(TAG, "Factory.create()")
            Log.d(TAG, "Requested ViewModel = ${modelClass.name}")

            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {

                Log.d(TAG, "Creating LoginViewModel")

                return LoginViewModel(authRepository) as T
            }

            Log.e(TAG, "Unknown ViewModel requested: ${modelClass.name}")

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "LoginViewModel destroyed")
    }
}