package mme.corp.audioshare.ui.screens.login

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.network.retrofit.HttpStatusException

/**
 * Only predefined, non-sensitive error messages reach the login UI.
 * Never display arbitrary HTTP bodies, transport exceptions, credentials, or token values.
 */
internal object LoginFailureMessage {
    fun forFailure(failure: Throwable): String = when (failure) {
        is ApiException -> when (failure.httpCode) {
            400 -> "The login details were rejected. Check your username and password."
            401 -> "Invalid username or password."
            403 -> "ServerRelay rejected the request (HTTP 403). Check its CSRF and access policy."
            429 -> "Too many login attempts. Try again later."
            502, 503, 504 -> "ServerRelay is temporarily unavailable (HTTP ${failure.httpCode})."
            else -> "ServerRelay rejected login (HTTP ${failure.httpCode})."
        }

        is HttpStatusException -> when (failure.statusCode) {
            400 -> "ServerRelay rejected the login request (HTTP 400)."
            401 -> "Invalid username or password (HTTP 401)."
            403 -> "ServerRelay denied login (HTTP 403). Check CSRF and access policy."
            429 -> "Too many login attempts (HTTP 429). Try again later."
            else -> "ServerRelay returned HTTP ${failure.statusCode}."
        }

        is UnknownHostException -> "Server address cannot be resolved. Check the configured backend host."
        is ConnectException -> "Cannot connect to ServerRelay. Check its address, port and firewall."
        is SocketTimeoutException -> "ServerRelay timed out. Check its availability and your network."
        is SSLException -> "ServerRelay TLS verification failed. Check its certificate and host."
        is IOException -> if (failure.message.orEmpty().contains("CSRF", ignoreCase = true)) {
            "Could not obtain the server CSRF proof. Check ServerRelay availability and security configuration."
        } else {
            "Network request failed. Check the server address and connection."
        }

        else -> "Login was not completed. Check ServerRelay response and try again."
    }
}
