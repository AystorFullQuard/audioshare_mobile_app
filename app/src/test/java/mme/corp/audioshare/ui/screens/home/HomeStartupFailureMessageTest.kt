package mme.corp.audioshare.ui.screens.home

import mme.corp.audioshare.exception.ApiErrorResponse
import mme.corp.audioshare.exception.ApiException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStartupFailureMessageTest {
    @Test
    fun deviceLimitFailureProvidesSafeAccountRecoveryHint() {
        val error = ApiException(
            httpCode = 409,
            apiError = ApiErrorResponse(
                code = "DEVICE_LIMIT_REACHED",
                message = "Do not echo server response details"
            )
        )
        val message = startupFailureMessage(error)
        assertTrue(message.contains("Unlink an old device"))
        assertFalse(message.contains("server response details"))
    }

    @Test
    fun unknownFailuresDoNotExposeBackendResponse() {
        val message = startupFailureMessage(IllegalStateException("sensitive server details"))
        assertTrue(message.contains("Unable to load rooms"))
        assertFalse(message.contains("sensitive"))
    }
}
