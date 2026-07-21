package mme.corp.audioshare.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mme.corp.audioshare.data.storage.SessionManager

class SplashViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _destination =
        MutableStateFlow<SplashDestination>(
            SplashDestination.Loading
        )

    val destination: StateFlow<SplashDestination> =
        _destination.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {

        viewModelScope.launch {

            val hasSession =
                sessionManager.hasActiveSession()

            _destination.value =
                if (hasSession) {
                    SplashDestination.Bootstrap
                } else {
                    SplashDestination.Login
                }

        }
    }

    companion object {

        fun Factory(
            sessionManager: SessionManager
        ): ViewModelProvider.Factory {

            return object : ViewModelProvider.Factory {

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>
                ): T {

                    return SplashViewModel(
                        sessionManager
                    ) as T
                }
            }
        }
    }
}