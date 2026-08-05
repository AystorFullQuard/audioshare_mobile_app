package mme.corp.audioshare.network.retrofit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ApiCallExecutorTest {

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