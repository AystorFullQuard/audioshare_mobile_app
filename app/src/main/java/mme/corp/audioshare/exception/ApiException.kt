package mme.corp.audioshare.exception

class ApiException(
    val httpCode: Int,
    val apiError: ApiErrorResponse
) : Exception(apiError.message)