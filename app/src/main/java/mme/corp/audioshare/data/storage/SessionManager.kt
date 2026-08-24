package mme.corp.audioshare.data.storage

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "session")

class SessionManager(
    private val context: Context
) : DeviceIdStore, TokenStore {

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

    val deviceId: Flow<String?> =
        context.dataStore.data
            .catch { exception ->

                Log.e(TAG, "deviceId flow exception", exception)

                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val currentUserId = preferences[USER_ID]
                val id = DeviceIdentityPreferences.read(
                    preferences = preferences,
                    userId = currentUserId
                )

                Log.v(
                    TAG,
                    "DeviceId requested. UserExists=${!currentUserId.isNullOrBlank()}, " +
                        "deviceExists=${!id.isNullOrBlank()}"
                )

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

                Log.d(TAG, "Clearing authentication session")

                clearAuthenticationSession(preferences)
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

    override suspend fun getAccessToken(): String? {

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

    suspend fun getSession(): Session {
        Log.d(TAG, "getSession()")

        val preferences = context.dataStore.data.first()

        return Session(

            accessToken = preferences[ACCESS_TOKEN],

            refreshToken = preferences[REFRESH_TOKEN],

            userId = preferences[USER_ID],

            sessionId = preferences[SESSION_ID],

            deviceId = DeviceIdentityPreferences.read(
                preferences = preferences,
                userId = preferences[USER_ID]
            )
        )
    }

    override suspend fun getDeviceId(): String? {
        Log.d(TAG, "getDeviceId()")

        return try {
            var resolvedDeviceId: String? = null

            context.dataStore.edit { preferences ->
                val currentUserId = preferences[USER_ID]
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)

                if (currentUserId != null) {
                    resolvedDeviceId = DeviceIdentityPreferences.migrateAndRead(
                        preferences = preferences,
                        userId = currentUserId
                    )
                }
            }

            Log.d(
                TAG,
                "Device id exists=${!resolvedDeviceId.isNullOrBlank()}"
            )
            resolvedDeviceId
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "getDeviceId() FAILED", exception)
            null
        }
    }

    override suspend fun saveDeviceId(deviceId: String) {
        require(deviceId.isNotBlank()) { "Device id must not be blank" }

        Log.d(TAG, "saveDeviceId()")

        context.dataStore.edit { preferences ->
            val currentUserId = requireCurrentUserId(preferences)
            DeviceIdentityPreferences.save(
                preferences = preferences,
                userId = currentUserId,
                deviceId = deviceId
            )
        }
    }

    override suspend fun clearDeviceId() {
        Log.d(TAG, "clearDeviceId()")

        context.dataStore.edit { preferences ->
            val currentUserId = preferences[USER_ID]
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@edit

            DeviceIdentityPreferences.clear(
                preferences = preferences,
                userId = currentUserId
            )
        }
    }

    override suspend fun getTokenSnapshot(): TokenSnapshot? {
        Log.d(TAG, "getTokenSnapshot()")

        return try {
            val preferences = context.dataStore.data.first()
            val accessToken = preferences[ACCESS_TOKEN]
            val refreshToken = preferences[REFRESH_TOKEN]

            if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
                Log.d(TAG, "Token snapshot unavailable")
                null
            } else {
                Log.d(TAG, "Token snapshot available")
                TokenSnapshot(
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "getTokenSnapshot() FAILED", exception)
            null
        }
    }

    suspend fun getRefreshToken(): String? {
        Log.d(TAG, "getRefreshToken()")

        return try {
            val token = refreshToken.first()

            Log.d(
                TAG,
                "Refresh token exists=${!token.isNullOrBlank()}"
            )

            token
        } catch (e: Exception) {

            Log.e(TAG, "getRefreshToken() FAILED", e)

            null
        }
    }

    override suspend fun updateTokens(
        accessToken: String,
        refreshToken: String
    ) {
        require(accessToken.isNotBlank()) { "Access token must not be blank" }
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank" }

        Log.d(TAG, "==========================================")
        Log.d(TAG, "updateTokens() START")
        Log.d(TAG, "AccessToken length=${accessToken.length}")
        Log.d(TAG, "RefreshToken length=${refreshToken.length}")

        try {

            context.dataStore.edit { preferences ->

                preferences[ACCESS_TOKEN] = accessToken
                preferences[REFRESH_TOKEN] = refreshToken
            }

            Log.i(TAG, "Tokens updated successfully")

        } catch (e: Exception) {

            Log.e(TAG, "updateTokens() FAILED", e)
            throw e

        } finally {

            Log.d(TAG, "updateTokens() END")
            Log.d(TAG, "==========================================")
        }
    }

    override suspend fun clearSessionIfMatches(
        expected: TokenSnapshot
    ): Boolean {
        Log.d(TAG, "==========================================")
        Log.d(TAG, "clearSessionIfMatches() START")

        var cleared = false

        try {
            context.dataStore.edit { preferences ->
                val currentAccessToken = preferences[ACCESS_TOKEN]
                val currentRefreshToken = preferences[REFRESH_TOKEN]

                if (
                    currentAccessToken == expected.accessToken &&
                    currentRefreshToken == expected.refreshToken
                ) {
                    clearAuthenticationSession(preferences)
                    cleared = true
                }
            }

            Log.i(TAG, "Session cleared after refresh rejection=$cleared")
            return cleared
        } catch (e: Exception) {
            Log.e(TAG, "clearSessionIfMatches() FAILED", e)
            throw e
        } finally {
            Log.d(TAG, "clearSessionIfMatches() END")
            Log.d(TAG, "==========================================")
        }
    }

    private fun clearAuthenticationSession(
        preferences: MutablePreferences
    ) {
        preferences.remove(ACCESS_TOKEN)
        preferences.remove(REFRESH_TOKEN)
        preferences.remove(USER_ID)
        preferences.remove(SESSION_ID)
    }

    private fun requireCurrentUserId(
        preferences: MutablePreferences
    ): String = preferences[USER_ID]
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error("Cannot persist device id without an authenticated user")

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