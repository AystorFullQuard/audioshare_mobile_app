package mme.corp.audioshare.exception

data class ApiErrorResponse(

    /**
     * Backend error code.
     * Example:
     * VALIDATION_ERROR
     * AUTHENTICATION_FAILED
     * ROOM_NOT_FOUND
     */
    val code: String,

    /**
     * Human-readable message.
     */
    val message: String,

    /**
     * Validation errors by field.
     *
     * Example:
     * {
     *   "password": "size must be between 8 and 128",
     *   "login": "must not be blank"
     * }
     */
    val fieldErrors: Map<String, String>? = emptyMap(),

    /**
     * Server timestamp.
     */
    val timestamp: String? = null
) {

    /**
     * Returns the most user-friendly error message.
     */
    fun displayMessage(): String {

        if (!fieldErrors.isNullOrEmpty()) {

            return buildString {

                append(message)

                fieldErrors.forEach { (field, error) ->
                    append("\n• ")
                    append(field)
                    append(": ")
                    append(error)
                }
            }
        }

        return message
    }

    /**
     * Returns a detailed string for Logcat.
     */
    fun debugString(): String =
        buildString {

            appendLine("ApiErrorResponse")
            appendLine("Code       : $code")
            appendLine("Message    : $message")
            appendLine("Timestamp  : $timestamp")

            if (fieldErrors.isNullOrEmpty()) {

                appendLine("FieldErrors: none")

            } else {

                appendLine("FieldErrors:")

                fieldErrors.forEach { (field, error) ->
                    appendLine(" - $field = $error")
                }
            }
        }

    override fun toString(): String = debugString()
}