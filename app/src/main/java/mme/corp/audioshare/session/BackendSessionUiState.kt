package mme.corp.audioshare.session

data class BackendSessionUiState(
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val userId: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val lastHeartbeatAt: String? = null,
    val error: String? = null
)
