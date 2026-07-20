package mme.corp.audioshare.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val SESSION_NAME = "session"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = SESSION_NAME
)

class SessionManager(
    private val context: Context
) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USERNAME = stringPreferencesKey("username")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    /**
     * Save the complete session in one transaction.
     */
    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        username: String,
        deviceId: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[Keys.ACCESS_TOKEN] = accessToken
            preferences[Keys.REFRESH_TOKEN] = refreshToken
            preferences[Keys.USERNAME] = username
            preferences[Keys.DEVICE_ID] = deviceId
        }
    }

    suspend fun saveAccessToken(token: String) {
        context.dataStore.edit {
            it[Keys.ACCESS_TOKEN] = token
        }
    }

    suspend fun saveRefreshToken(token: String) {
        context.dataStore.edit {
            it[Keys.REFRESH_TOKEN] = token
        }
    }

    suspend fun saveUsername(username: String) {
        context.dataStore.edit {
            it[Keys.USERNAME] = username
        }
    }

    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit {
            it[Keys.DEVICE_ID] = deviceId
        }
    }

    val accessToken: Flow<String?>
        get() = context.dataStore.data.map {
            it[Keys.ACCESS_TOKEN]
        }

    val refreshToken: Flow<String?>
        get() = context.dataStore.data.map {
            it[Keys.REFRESH_TOKEN]
        }

    val username: Flow<String?>
        get() = context.dataStore.data.map {
            it[Keys.USERNAME]
        }

    val deviceId: Flow<String?>
        get() = context.dataStore.data.map {
            it[Keys.DEVICE_ID]
        }

    suspend fun getAccessToken(): String? {
        return accessToken.first()
    }

    suspend fun getRefreshToken(): String? {
        return refreshToken.first()
    }

    suspend fun getUsername(): String? {
        return username.first()
    }

    suspend fun getDeviceId(): String? {
        return deviceId.first()
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.clear()
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return !getAccessToken().isNullOrBlank()
    }
}