package mme.corp.audioshare.session

import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.time.Instant

object JwtSessionDecoder {

    private const val CLAIM_SESSION_ID = "sessionId"
    private const val CLAIM_SUBJECT = "sub"
    private const val CLAIM_TOKEN_TYPE = "tokenType"
    private const val CLAIM_EXPIRATION = "exp"

    fun sessionId(jwt: String): String =
        payload(jwt).getString(CLAIM_SESSION_ID)

    fun userId(jwt: String): String =
        payload(jwt).getString(CLAIM_SUBJECT)

    fun tokenType(jwt: String): String =
        payload(jwt).getString(CLAIM_TOKEN_TYPE)

    fun expiresAt(jwt: String): Instant =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Instant.ofEpochSecond(
                payload(jwt).getLong(CLAIM_EXPIRATION)
            )
        } else {
            TODO("VERSION.SDK_INT < O")
        }

    private fun payload(jwt: String): JSONObject {

        val parts = jwt.split('.')

        require(parts.size == 3) {
            "Invalid JWT format"
        }

        val decoded = Base64.decode(
            parts[1],
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        return JSONObject(String(decoded))
    }
}