package mme.corp.audioshare.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mme.corp.audioshare.data.repository.AuthRepository
import mme.corp.audioshare.data.storage.SessionManager

class HomeViewModel(

    private val authRepository: AuthRepository,

    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> =
        _uiState.asStateFlow()

    fun loadSession() {
        viewModelScope.launch {
            val session = sessionManager.getSession()

            _uiState.update {
                it.copy(

                    accessTokenExists =
                        !session.accessToken.isNullOrBlank(),

                    refreshTokenExists =
                        !session.refreshToken.isNullOrBlank(),

                    accessTokenLength =
                        session.accessToken?.length ?: 0,

                    refreshTokenLength =
                        session.refreshToken?.length ?: 0,

                    userId =
                        session.userId,

                    sessionId =
                        session.sessionId
                )
            }
        }
    }

    fun clearSession() {

        viewModelScope.launch {

            _uiState.update {

                it.copy(
                    isLoading = true,
                    lastMessage = "Clearing session..."
                )
            }

            try {

                sessionManager.clearSession()

                loadSession()

                _uiState.update {

                    it.copy(
                        isLoading = false,
                        lastMessage = "Session cleared successfully."
                    )
                }

            } catch (e: Exception) {

                _uiState.update {

                    it.copy(
                        isLoading = false,
                        lastMessage = e.message ?: "Failed to clear session."
                    )
                }
            }
        }
    }

    fun refreshToken() {

        android.util.Log.d("HomeVM", "refreshToken() ENTER")

        viewModelScope.launch {

            android.util.Log.d("HomeVM", "Coroutine START")

            _uiState.update {
                it.copy(
                    isLoading = true,
                    lastMessage = "Refreshing token..."
                )
            }

            android.util.Log.d("HomeVM", "Calling repository")

            val result = authRepository.refresh()

            android.util.Log.d("HomeVM", "Repository returned")

            result.onSuccess {

                android.util.Log.d("HomeVM", "SUCCESS")

                loadSession()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastMessage = "Token refreshed successfully."
                    )
                }
            }

            result.onFailure { error ->

                android.util.Log.e("HomeVM", "FAILURE", error)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastMessage = error.message ?: "Refresh failed."
                    )
                }
            }
        }
    }
}