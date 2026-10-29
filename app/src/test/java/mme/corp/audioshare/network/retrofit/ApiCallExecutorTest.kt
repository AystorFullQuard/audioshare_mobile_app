package mme.corp.audioshare.network.retrofit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import org.junit.Test

class ApiCallExecutorTest {

    @Test
    fun blockingCallRethrowsCancellationException() {
        val expected = CancellationException("cancelled")

        try {
            executeBlockingApiCall<Unit>("unused") {
                throw expected
            }
            fail("CancellationException must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }


    @Test
    fun unexpectedHttpErrorBodyIsNotShownInClientFailure() = runTest {
        val secret = "SENSITIVE_RESPONSE_SENTINEL"
        val result = executeApiCall<String>("unused") {
            Response.error(403, "<html>$secret</html>".toResponseBody())
        }
        assertTrue(result.exceptionOrNull() is HttpStatusException)
        assertTrue(result.exceptionOrNull().toString().contains("HTTP 403"))
        assertTrue(!result.exceptionOrNull().toString().contains(secret))
    }

    @Test
    fun noBodyCallRethrowsCancellationException() = runTest {
        val expected = CancellationException("cancelled")

        try {
            executeApiCallWithoutBody {
                throw expected
            }
            fail("CancellationException must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }
}