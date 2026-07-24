package mme.corp.audioshare.ui.screens.home

data class HomeUiState(

    val isLoading: Boolean = false,

    val accessTokenExists: Boolean = false,

    val refreshTokenExists: Boolean = false,

    val accessTokenLength: Int = 0,

    val refreshTokenLength: Int = 0,

    val userId: String? = null,

    val sessionId: String? = null,

    val lastMessage: String = ""
)