package mme.corp.audioshare.network.retrofit

import java.net.URI

/**
 * Single safety gate for every ServerRelay Retrofit entry point.
 * Production: HTTPS only. Development: HTTPS, or HTTP to a loopback/private LAN target.
 * This is *not* a license to use HTTP outside controlled debug networks.
 */
internal object ApiEndpointPolicy {

    fun approvedBaseUrl(configuredUrl: String, isDebug: Boolean): String {
        val trimmed = configuredUrl.trim()
        require(trimmed.isNotEmpty()) {
            "ServerRelay API URL is not configured for this build"
        }

        val uri = try {
            URI(trimmed)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid ServerRelay API URL", error)
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        require(
            !host.isNullOrBlank() && uri.userInfo == null &&
                    uri.rawQuery == null && uri.rawFragment == null &&
                    (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
                    (uri.port in -1..65535) && uri.port != 0
        ) {
            "ServerRelay API URL must be a bare origin without credentials, path, query or fragment"
        }

        when (scheme) {
            "https" -> Unit
            "http" -> require(isDebug && isLocalDevelopmentHost(host)) {
                "Cleartext ServerRelay API is allowed only for private debug LAN endpoints"
            }

            else -> throw IllegalArgumentException("ServerRelay API must use http or https")
        }
        // Normalize the protocol spelling so the transport and endpoint tests share one origin.
        // Do not normalize, follow or silently rewrite a cross-origin redirect.
        return "$scheme://${uri.rawAuthority}/"
    }

    private fun isLocalDevelopmentHost(host: String): Boolean {
        if (host == "localhost") {
            return true
        }
        if (host == "[::1]") return true
        val octets = host.split('.')
        if (octets.size != 4) return false
        val values = octets.map { octet ->
            if (octet.isEmpty() || octet.length > 3 || octet.any { !it.isDigit() }) return false
            octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }
        return values[0] == 10 ||
                (values[0] == 172 && values[1] in 16..31) ||
                (values[0] == 192 && values[1] == 168) ||
                values[0] == 127 ||
                (values[0] == 169 && values[1] == 254)
    }
}
