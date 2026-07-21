package mme.corp.audioshare.ui.screens.login

data class LoginUiState(
    val login: String = "",

    val password: String = "",

    val isLoading: Boolean = false,

    val error: String? = null,

    val isLoggedIn: Boolean = false
)