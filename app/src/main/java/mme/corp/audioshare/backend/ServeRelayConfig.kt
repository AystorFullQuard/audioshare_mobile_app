package mme.corp.audioshare.backend

data class ServeRelayConfig(
    val baseUrl: String,
    val enableHttpLogging: Boolean = false,
    val connectTimeoutSeconds: Long = DEFAULT_CONNECT_TIMEOUT_SECONDS,
    val readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT_SECONDS,
    val writeTimeoutSeconds: Long = DEFAULT_WRITE_TIMEOUT_SECONDS
) {
    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "ServeRelay baseUrl must use http or https"
        }
        require(baseUrl.endsWith('/')) {
            "ServeRelay baseUrl must end with '/'"
        }
        require(connectTimeoutSeconds > 0)
        require(readTimeoutSeconds > 0)
        require(writeTimeoutSeconds > 0)
    }

    companion object {
        const val ANDROID_EMULATOR_LOCAL_URL = "http://10.0.2.2:8090/"
        private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 10L
        private const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
        private const val DEFAULT_WRITE_TIMEOUT_SECONDS = 30L
    }
}
