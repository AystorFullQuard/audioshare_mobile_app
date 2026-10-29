package mme.corp.audioshare.ui.screens.login

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import mme.corp.audioshare.network.retrofit.HttpStatusException

class LoginFailureMessageTest {
    @Test
    fun distinguishesNetworkAndCsrfErrorsWithoutExposingSecrets() {
        val secret = "SECRET_SENTINEL"
        val csrf = LoginFailureMessage.forFailure(IOException("CSRF invalid: $secret"))
        assertTrue(csrf.contains("CSRF"))
        assertFalse(csrf.contains(secret))
        assertTrue(
            LoginFailureMessage.forFailure(ConnectException("$secret"))
                .contains("Cannot connect")
        )
        assertTrue(
            LoginFailureMessage.forFailure(UnknownHostException("$secret"))
                .contains("server", ignoreCase = true)
        )
        assertFalse(
            LoginFailureMessage.forFailure(IllegalStateException("$secret"))
                .contains(secret)
        )
        assertTrue(LoginFailureMessage.forFailure(HttpStatusException(403)).contains("403"))
    }
}
