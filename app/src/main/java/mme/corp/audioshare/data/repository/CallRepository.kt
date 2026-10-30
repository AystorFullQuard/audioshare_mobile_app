package mme.corp.audioshare.data.repository

import mme.corp.audioshare.data.api.CallsApi
import mme.corp.audioshare.data.dto.call.CallSnapshot
import mme.corp.audioshare.network.retrofit.executeApiCall
import java.util.UUID

/** Online call control (not an offline permission or encrypted media session). */
class CallRepository(private val api: CallsApi) {
    suspend fun start(roomId: String): Result<CallSnapshot> = checked(roomId) {
        executeApiCall("Missing started call") { api.start(roomId) }
    }

    suspend fun current(roomId: String): Result<CallSnapshot> = checked(roomId) {
        executeApiCall("Missing current call") { api.current(roomId) }
    }

    suspend fun get(callId: String): Result<CallSnapshot> = checked(callId) {
        executeApiCall("Missing call") { api.get(callId) }
    }

    suspend fun join(callId: String): Result<CallSnapshot> = checked(callId) {
        executeApiCall("Missing joined call") { api.join(callId) }
    }

    suspend fun leave(callId: String): Result<CallSnapshot> = checked(callId) {
        executeApiCall("Missing call leave result") { api.leave(callId) }
    }

    suspend fun end(callId: String): Result<CallSnapshot> = checked(callId) {
        executeApiCall("Missing ended call") { api.end(callId) }
    }

    private suspend fun <T> checked(id: String, request: suspend () -> Result<T>): Result<T> =
        if (runCatching {
                UUID.fromString(id).toString().equals(id, true)
            }.getOrDefault(false)) request()
        else Result.failure(IllegalArgumentException("Expected a ServerRelay UUID"))
}
