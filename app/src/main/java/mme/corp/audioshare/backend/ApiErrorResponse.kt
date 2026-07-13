package mme.corp.audioshare.backend

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
    val timestamp: String? = null
)
