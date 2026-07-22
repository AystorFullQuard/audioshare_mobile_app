package mme.corp.audioshare.data.storage

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "session")

class SessionManager(
    private val context: Context
) {

    companion object {

        private const val TAG = "SessionManager"

        private val ACCESS_TOKEN =
            stringPreferencesKey("access_token")

        private val REFRESH_TOKEN =
            stringPreferencesKey("refresh_token")

        private val USER_ID =
            stringPreferencesKey("user_id")

        private val SESSION_ID =
            stringPreferencesKey("session_id")
    }

    init {
        Log.d(TAG, "==========================================")
        Log.d(TAG, "SessionManager created")
    }

    val accessToken: Flow<String?> =
        context.dataStore.data
            .catch { exception ->

                Log.e(TAG, "accessToken flow exception", exception)

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->

                val token = preferences[ACCESS_TOKEN]

                Log.v(
                    TAG,
                    "Access token requested. Exists=${!token.isNullOrBlank()}"
                )

                token
            }

    val refreshToken: Flow<String?> =
        context.dataStore.data
            .catch { exception ->

                Log.e(TAG, "refreshToken flow exception", exception)

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->

                val token = preferences[REFRESH_TOKEN]

                Log.v(
                    TAG,
                    "Refresh token requested. Exists=${!token.isNullOrBlank()}"
                )

                token
            }

    val userId: Flow<String?> =
        context.dataStore.data
            .catch { exception ->

                Log.e(TAG, "userId flow exception", exception)

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->

                val id = preferences[USER_ID]

                Log.v(TAG, "UserId requested = $id")

                id
            }

    val sessionId: Flow<String?> =
        context.dataStore.data
            .catch { exception ->

                Log.e(TAG, "sessionId flow exception", exception)

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->

                val id = preferences[SESSION_ID]

                Log.v(TAG, "SessionId requested = $id")

                id
            }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        sessionId: String
    ) {

        Log.d(TAG, "==========================================")
        Log.d(TAG, "saveSession() START")
        Log.d(TAG, "UserId=$userId")
        Log.d(TAG, "SessionId=$sessionId")
        Log.d(TAG, "AccessToken length=${accessToken.length}")
        Log.d(TAG, "RefreshToken length=${refreshToken.length}")

        try {

            context.dataStore.edit { preferences: MutablePreferences ->

                Log.d(TAG, "Writing ACCESS_TOKEN")
                preferences[ACCESS_TOKEN] = accessToken

                Log.d(TAG, "Writing REFRESH_TOKEN")
                preferences[REFRESH_TOKEN] = refreshToken

                Log.d(TAG, "Writing USER_ID")
                preferences[USER_ID] = userId

                Log.d(TAG, "Writing SESSION_ID")
                preferences[SESSION_ID] = sessionId
            }

            Log.i(TAG, "Session saved successfully")

        } catch (e: Exception) {

            Log.e(TAG, "saveSession() FAILED", e)

            throw e

        } finally {

            Log.d(TAG, "saveSession() END")
            Log.d(TAG, "==========================================")
        }
    }

    suspend fun clearSession() {

        Log.d(TAG, "==========================================")
        Log.d(TAG, "clearSession() START")

        try {

            context.dataStore.edit { preferences ->

                Log.d(TAG, "Clearing DataStore")

                preferences.clear()
            }

            Log.i(TAG, "Session cleared")

        } catch (e: Exception) {

            Log.e(TAG, "clearSession() FAILED", e)

            throw e

        } finally {

            Log.d(TAG, "clearSession() END")
            Log.d(TAG, "==========================================")
        }
    }

    suspend fun getAccessToken(): String? {

        Log.d(TAG, "getAccessToken()")

        return try {

            val token = accessToken.first()

            Log.d(
                TAG,
                "Access token exists=${!token.isNullOrBlank()}"
            )

            token

        } catch (e: Exception) {

            Log.e(TAG, "getAccessToken() FAILED", e)

            null
        }
    }

    suspend fun hasActiveSession(): Boolean {

        Log.d(TAG, "==========================================")
        Log.d(TAG, "hasActiveSession()")

        return try {

            val token = accessToken.first()

            Log.d(TAG, "Token exists=${token != null}")
            Log.d(TAG, "Token blank=${token.isNullOrBlank()}")

            val active = !token.isNullOrBlank()

            Log.d(TAG, "Active session=$active")
            Log.d(TAG, "==========================================")

            active

        } catch (e: Exception) {

            Log.e(TAG, "hasActiveSession() FAILED", e)
            Log.d(TAG, "==========================================")

            false
        }
    }
}