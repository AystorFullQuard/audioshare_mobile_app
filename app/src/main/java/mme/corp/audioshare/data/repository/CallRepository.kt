package mme.corp.audioshare.data.repository

import mme.corp.audioshare.data.api.CallsApi
import mme.corp.audioshare.data.dto.call.CallSnapshot
import mme.corp.audioshare.network.retrofit.executeApiCall

/** Online call control (not an offline permission or encrypted media session). */
class CallRepository(private val api: CallsApi) {
    suspend fun start(roomId: String): Result<CallSnapshot> =
        executeApiCall("Missing started call") { api.start(roomId) }

    suspend fun current(roomId: String): Result<CallSnapshot> =
        executeApiCall("Missing current call") { api.current(roomId) }

    suspend fun get(callId: String): Result<CallSnapshot> =
        executeApiCall("Missing call") { api.get(callId) }

    suspend fun join(callId: String): Result<CallSnapshot> =
        executeApiCall("Missing joined call") { api.join(callId) }

    suspend fun leave(callId: String): Result<CallSnapshot> =
        executeApiCall("Missing call leave result") { api.leave(callId) }

    suspend fun end(callId: String): Result<CallSnapshot> =
        executeApiCall("Missing ended call") { api.end(callId) }
}
