package mme.corp.audioshare.ui.screens.bootstrap

data class BootstrapUiState(
    val isLoading: Boolean = true,
    val isComplete: Boolean = false,
    val status: String = "Connecting to ServeRelay...",
    val error: String? = null
)
