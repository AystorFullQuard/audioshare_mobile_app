package mme.corp.audioshare.network.retrofit

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import mme.corp.audioshare.exception.ApiErrorResponse
import mme.corp.audioshare.exception.ApiException
import retrofit2.Response

private val errorGson = Gson()

internal suspend fun <T : Any> executeApiCall(
    emptyBodyMessage: String,
    call: suspend () -> Response<T>
): Result<T> {
    return try {
        val response = call()

        if (response.isSuccessful) {
            val body = response.body()

            if (body != null) {
                Result.success(body)
            } else {
                Result.failure(IllegalStateException(emptyBodyMessage))
            }
        } else {
            Result.failure(response.toApiException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

private fun Response<*>.toApiException(): Exception {
    val rawError = runCatching {
        errorBody()?.string()
    }.getOrNull()

    val parsedError = rawError
        ?.takeIf(String::isNotBlank)
        ?.let { body ->
            runCatching {
                errorGson.fromJson(body, ApiErrorResponse::class.java)
            }.getOrNull()
        }

    return if (parsedError != null) {
        ApiException(
            httpCode = code(),
            apiError = parsedError
        )
    } else {
        IllegalStateException(
            rawError ?: "HTTP ${code()} ${message()}"
        )
    }
}
