package mme.corp.audioshare.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import mme.corp.audioshare.data.repository.AuthRepository
import mme.corp.audioshare.data.storage.SessionManager

class HomeViewModelFactory(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                authRepository,
                sessionManager
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}