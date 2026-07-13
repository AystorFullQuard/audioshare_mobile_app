package mme.corp.audioshare.backend.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.serveRelaySessionDataStore by preferencesDataStore(
    name = "serverelay_session"
)

data class BackendSession(
    val userId: String? = null,
    val sessionId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val deviceId: String? = null
)

interface SessionStore {
    val session: StateFlow<BackendSession>

    suspend fun load(): BackendSession

    fun currentAccessToken(): String?

    suspend fun saveAuthentication(
        userId: String,
        sessionId: String,
        accessToken: String,
        refreshToken: String
    )

    suspend fun saveDeviceId(deviceId: String)

    suspend fun clear()
}

class DataStoreSessionStore(
    context: Context
) : SessionStore {
    private val dataStore = context.applicationContext.serveRelaySessionDataStore
    private val cachedSession = MutableStateFlow(BackendSession())

    override val session: StateFlow<BackendSession> = cachedSession.asStateFlow()

    private val persistedSession: Flow<BackendSession> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(::toBackendSession)

    override suspend fun load(): BackendSession = persistedSession
        .first()
        .also { cachedSession.value = it }

    override fun currentAccessToken(): String? = cachedSession.value.accessToken

    override suspend fun saveAuthentication(
        userId: String,
        sessionId: String,
        accessToken: String,
        refreshToken: String
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.USER_ID] = userId
            preferences[Keys.SESSION_ID] = sessionId
            preferences[Keys.ACCESS_TOKEN] = accessToken
            preferences[Keys.REFRESH_TOKEN] = refreshToken
        }
        load()
    }

    override suspend fun saveDeviceId(deviceId: String) {
        dataStore.edit { preferences ->
            preferences[Keys.DEVICE_ID] = deviceId
        }
        load()
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
        cachedSession.value = BackendSession()
    }

    private fun toBackendSession(preferences: Preferences): BackendSession = BackendSession(
        userId = preferences[Keys.USER_ID],
        sessionId = preferences[Keys.SESSION_ID],
        accessToken = preferences[Keys.ACCESS_TOKEN],
        refreshToken = preferences[Keys.REFRESH_TOKEN],
        deviceId = preferences[Keys.DEVICE_ID]
    )

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val SESSION_ID = stringPreferencesKey("session_id")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
