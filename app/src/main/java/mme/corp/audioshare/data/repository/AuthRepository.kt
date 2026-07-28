package mme.corp.audioshare.data.repository

import android.util.Log
import mme.corp.audioshare.data.api.AuthApi
import mme.corp.audioshare.data.dto.LoginRequest
import mme.corp.audioshare.data.dto.LoginResponse
import mme.corp.audioshare.data.storage.SessionManager
import com.google.gson.Gson
import mme.corp.audioshare.data.dto.RefreshRequest
import mme.corp.audioshare.data.dto.RefreshResponse
import mme.corp.audioshare.exception.ApiErrorResponse
import mme.corp.audioshare.exception.ApiException
import mme.corp.audioshare.presence.PresenceRuntimeController

class AuthRepository(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val presenceRuntimeController: PresenceRuntimeController =
        PresenceRuntimeController.NO_OP
) {

    private val gson = Gson()

    companion object {
        private const val TAG = "AuthRepo"
    }

    init {
        Log.d(TAG, "==========================================")
        Log.d(TAG, "AuthRepository created")
    }

    suspend fun login(
        login: String,
        password: String
    ): Result<LoginResponse> {

        Log.d(TAG, "==========================================")
        Log.d(TAG, "LOGIN REQUEST START")
        Log.d(TAG, "Username = $login")
        Log.d(TAG, "Password length = ${password.length}")

        return try {

            Log.d(TAG, "Creating LoginRequest")

            val request = LoginRequest(
                login = login,
                password = password
            )

            Log.d(TAG, "Calling authApi.login()")

            val response = authApi.login(request)

            Log.d(TAG, "HTTP response received")
            Log.d(TAG, "HTTP Code = ${response.code()}")
            Log.d(TAG, "HTTP Message = ${response.message()}")
            Log.d(TAG, "Successful = ${response.isSuccessful}")

            if (!response.isSuccessful) {

                Log.e(TAG, "==========================================")
                Log.e(TAG, "HTTP REQUEST FAILED")
                Log.e(TAG, "HTTP Code = ${response.code()}")
                Log.e(TAG, "HTTP Message = ${response.message()}")

                val rawError = try {
                    response.errorBody()?.string()
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to read error body", e)
                    null
                }

                Log.e(TAG, "Raw error body:")
                Log.e(TAG, rawError ?: "<empty>")

                val apiError = try {

                    rawError?.let {
                        gson.fromJson(it, ApiErrorResponse::class.java)
                    }

                } catch (e: Exception) {

                    Log.e(TAG, "Unable to parse ApiErrorResponse", e)
                    null
                }

                if (apiError != null) {

                    Log.e(TAG, apiError.debugString())

                    return Result.failure(
                        ApiException(
                            httpCode = response.code(),
                            apiError = apiError
                        )
                    )
                }

                Log.e(TAG, "Backend returned unknown error format")

                return Result.failure(
                    Exception(
                        rawError
                            ?: "HTTP ${response.code()} ${response.message()}"
                    )
                )
            }

            Log.d(TAG, "Reading response body")

            val body = response.body()

            if (body == null) {

                Log.e(TAG, "Response body is NULL")

                return Result.failure(
                    Exception("Empty response")
                )
            }

            Log.i(TAG, "Login successful")
            Log.d(TAG, "UserId       : ${body.userId}")
            Log.d(TAG, "SessionId    : ${body.sessionId}")
            Log.d(TAG, "TokenType    : ${body.tokenType}")
            Log.d(TAG, "AccessToken  : ${body.accessToken.take(20)}...")
            Log.d(TAG, "RefreshToken : ${body.refreshToken.take(20)}...")

            Log.d(TAG, "Saving session")

            sessionManager.saveSession(
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
                userId = body.userId,
                sessionId = body.sessionId
            )

            Log.d(TAG, "Session saved successfully")

            Log.i(TAG, "LOGIN REQUEST FINISHED SUCCESSFULLY")
            Log.d(TAG, "==========================================")

            Result.success(body)

        } catch (e: Exception) {
            Log.e(TAG, "==========================================")
            Log.e(TAG, "NETWORK EXCEPTION")
            Log.e(TAG, "Type    : ${e::class.java.simpleName}")
            Log.e(TAG, "Message : ${e.message}", e)
            Log.e(TAG, "==========================================")
            Result.failure(e)
        }
    }

    suspend fun refresh(): Result<RefreshResponse> {

        Log.d(TAG, "==========================================")
        Log.d(TAG, "REFRESH REQUEST START")

        return try {

            Log.d(TAG, "Reading stored refresh token")

            val refreshToken = sessionManager.getRefreshToken()

            if (refreshToken.isNullOrBlank()) {

                Log.e(TAG, "No refresh token stored")

                return Result.failure(
                    Exception("Refresh token not found")
                )
            }

            Log.d(TAG, "Creating RefreshRequest")

            val request = RefreshRequest(
                refreshToken = refreshToken
            )

            Log.d(TAG, "Calling authApi.refresh()")

            val response = authApi.refresh(request)

            Log.d(TAG, "HTTP response received")
            Log.d(TAG, "HTTP Code = ${response.code()}")
            Log.d(TAG, "HTTP Message = ${response.message()}")
            Log.d(TAG, "Successful = ${response.isSuccessful}")

            if (!response.isSuccessful) {

                Log.e(TAG, "==========================================")
                Log.e(TAG, "REFRESH FAILED")

                val rawError = try {
                    response.errorBody()?.string()
                } catch (e: Exception) {
                    null
                }

                val apiError = try {

                    rawError?.let {
                        gson.fromJson(it, ApiErrorResponse::class.java)
                    }

                } catch (e: Exception) {

                    null
                }

                if (apiError != null) {

                    Log.e(TAG, apiError.debugString())

                    return Result.failure(
                        ApiException(
                            httpCode = response.code(),
                            apiError = apiError
                        )
                    )
                }

                return Result.failure(
                    Exception(
                        rawError ?: "HTTP ${response.code()} ${response.message()}"
                    )
                )
            }

            val body = response.body()

            if (body == null) {

                Log.e(TAG, "Response body is NULL")

                return Result.failure(
                    Exception("Empty response")
                )
            }

            Log.d(TAG, "Updating stored tokens")

            sessionManager.saveSession(
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
                userId = body.userId,
                sessionId = body.sessionId
            )

            Log.i(TAG, "Refresh successful")
            Log.d(TAG, "AccessToken  : ${body.accessToken.take(20)}...")
            Log.d(TAG, "RefreshToken : ${body.refreshToken.take(20)}...")
            Log.d(TAG, "==========================================")

            Result.success(body)

        } catch (e: Exception) {

            Log.e(TAG, "==========================================")
            Log.e(TAG, "REFRESH EXCEPTION")
            Log.e(TAG, "Type    : ${e::class.java.simpleName}")
            Log.e(TAG, "Message : ${e.message}", e)
            Log.e(TAG, "==========================================")

            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> {

        Log.d(TAG, "==========================================")
        Log.d(TAG, "LOGOUT START")

        presenceRuntimeController.stop()

        return try {

            Log.d(TAG, "Calling authApi.logout()")

            val networkResult = runCatching {
                authApi.logout()
            }

            if (networkResult.isSuccess) {

                Log.d(TAG, "Backend logout completed")

            } else {

                Log.w(
                    TAG,
                    "Backend logout failed: ${networkResult.exceptionOrNull()?.message}"
                )
            }

            Log.d(TAG, "Clearing local session")

            sessionManager.clearSession()

            Log.d(TAG, "Local session cleared")

            Log.i(TAG, "LOGOUT FINISHED")

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e(TAG, "LOGOUT CRASHED", e)

            try {

                Log.d(TAG, "Attempting emergency session cleanup")

                sessionManager.clearSession()

                Log.d(TAG, "Emergency cleanup completed")

            } catch (cleanupException: Exception) {

                Log.e(
                    TAG,
                    "Emergency cleanup failed",
                    cleanupException
                )
            }

            Result.failure(e)

        } finally {

            Log.d(TAG, "==========================================")
        }
    }
}