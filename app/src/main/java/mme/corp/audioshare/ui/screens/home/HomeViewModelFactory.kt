package mme.corp.audioshare.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import mme.corp.audioshare.data.repository.AuthRepository
import mme.corp.audioshare.data.storage.SessionManager
import mme.corp.audioshare.presence.PresenceRuntimeController

class HomeViewModelFactory(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val presenceRuntimeController: PresenceRuntimeController =
        PresenceRuntimeController.NO_OP
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                authRepository,
                sessionManager,
                presenceRuntimeController
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}