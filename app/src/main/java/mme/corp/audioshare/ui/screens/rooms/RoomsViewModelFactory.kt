package mme.corp.audioshare.ui.screens.rooms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import mme.corp.audioshare.room.RoomSessionCoordinator

class RoomsViewModelFactory(
    private val coordinator: RoomSessionCoordinator
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T = createViewModel(modelClass, extras.createSavedStateHandle())

    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        createViewModel(modelClass, SavedStateHandle())

    @Suppress("UNCHECKED_CAST")
    private fun <T : ViewModel> createViewModel(
        modelClass: Class<T>,
        savedStateHandle: SavedStateHandle
    ): T {
        require(modelClass.isAssignableFrom(RoomsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return RoomsViewModel(coordinator, savedStateHandle) as T
    }
}
