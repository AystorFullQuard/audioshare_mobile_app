package mme.corp.audioshare.ui.screens.splash

import android.util.Log
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

    companion object {

        private const val TAG = "SplashVM"

        fun Factory(
            sessionManager: SessionManager
        ): ViewModelProvider.Factory {

            Log.d(TAG, "Creating SplashViewModel.Factory")

            return object : ViewModelProvider.Factory {

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>
                ): T {

                    Log.d(TAG, "Factory.create()")
                    Log.d(TAG, "Requested ViewModel=${modelClass.name}")

                    if (!modelClass.isAssignableFrom(SplashViewModel::class.java)) {

                        Log.e(TAG, "Unknown ViewModel requested")

                        throw IllegalArgumentException(
                            "Unknown ViewModel class: ${modelClass.name}"
                        )
                    }

                    Log.d(TAG, "SplashViewModel created by Factory")

                    return SplashViewModel(
                        sessionManager
                    ) as T
                }
            }
        }
    }

    private val _destination =
        MutableStateFlow<SplashDestination>(
            SplashDestination.Loading
        )

    val destination: StateFlow<SplashDestination> =
        _destination.asStateFlow()

    init {

        Log.d(TAG, "==========================================")
        Log.d(TAG, "SplashViewModel created")
        Log.d(TAG, "Initial destination=${_destination.value}")

        checkSession()
    }

    private fun checkSession() {

        Log.d(TAG, "checkSession() START")

        viewModelScope.launch {

            try {

                Log.d(TAG, "Calling SessionManager.hasActiveSession()")

                val hasSession =
                    sessionManager.hasActiveSession()

                Log.d(TAG, "SessionManager returned=$hasSession")

                if (hasSession) {

                    Log.i(TAG, "User already authenticated")
                    Log.i(TAG, "Destination -> Bootstrap")

                    _destination.value =
                        SplashDestination.Bootstrap

                } else {

                    Log.i(TAG, "No active session")
                    Log.i(TAG, "Destination -> Login")

                    _destination.value =
                        SplashDestination.Login
                }

                Log.d(TAG, "Destination updated=${_destination.value}")

            } catch (e: Exception) {

                Log.e(TAG, "Session verification crashed", e)

                Log.w(TAG, "Fallback destination -> Login")

                _destination.value =
                    SplashDestination.Login

            } finally {

                Log.d(TAG, "checkSession() END")
                Log.d(TAG, "==========================================")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        Log.d(TAG, "SplashViewModel destroyed")
    }
}