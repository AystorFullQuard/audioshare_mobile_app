package mme.corp.audioshare.backend

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

sealed interface ApiResult<out T> {
    data class Success<T>(
        val value: T,
        val statusCode: Int
    ) : ApiResult<T>

    data class Failure(
        val statusCode: Int? = null,
        val error: ApiErrorResponse? = null,
        val cause: Throwable? = null
    ) : ApiResult<Nothing>
}

internal suspend fun <T : Any> executeApiCall(
    json: Json,
    call: suspend () -> Response<T>
): ApiResult<T> = try {
    val response = call()
    val body = response.body()

    if (response.isSuccessful && body != null) {
        ApiResult.Success(
            value = body,
            statusCode = response.code()
        )
    } else {
        ApiResult.Failure(
            statusCode = response.code(),
            error = response.errorBody()
                ?.string()
                ?.takeIf(String::isNotBlank)
                ?.let { errorBody ->
                    runCatching {
                        json.decodeFromString<ApiErrorResponse>(errorBody)
                    }.getOrNull()
                }
        )
    }
} catch (exception: CancellationException) {
    throw exception
} catch (exception: IOException) {
    ApiResult.Failure(cause = exception)
} catch (exception: SerializationException) {
    ApiResult.Failure(cause = exception)
} catch (exception: RuntimeException) {
    ApiResult.Failure(cause = exception)
}
