package mme.corp.audioshare.data.storage

import android.content.Context
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "session")

/** DataStore owns atomic snapshots; tokens are encrypted by AndroidKeyStore before persistence. */
class SessionManager(context: Context) : DeviceIdStore, InstallationIdStore, TokenStore {
    private val store = context.applicationContext.dataStore
    private val cipher = AndroidTokenCipher()
    private val migrationLock = Mutex()
    private var legacyMigrated = false

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val SESSION_ID = stringPreferencesKey("session_id")
    }

    /** One-time atomic upgrade of pre-GAP-08 unencrypted preferences. */
    private suspend fun migrateLegacyTokens() = migrationLock.withLock {
        if (legacyMigrated) return@withLock
        store.edit { prefs ->
            listOf(ACCESS_TOKEN, REFRESH_TOKEN).forEach { key ->
                val value = prefs[key] ?: return@forEach
                if (!cipher.isEnvelope(value)) prefs[key] = cipher.seal(value)
            }
        }
        legacyMigrated = true
    }

    private val safeData = store.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .onStart { migrateLegacyTokens() }

    val accessToken: Flow<String?> = safeData.map { cipher.open(it[ACCESS_TOKEN]) }
    val refreshToken: Flow<String?> = safeData.map { cipher.open(it[REFRESH_TOKEN]) }
    val userId: Flow<String?> = safeData.map { it[USER_ID] }
    val sessionId: Flow<String?> = safeData.map { it[SESSION_ID] }
    val deviceId: Flow<String?> = safeData.map { prefs ->
        DeviceIdentityPreferences.read(prefs, prefs[USER_ID])
    }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        sessionId: String
    ) {
        require(accessToken.isNotBlank() && refreshToken.isNotBlank())
        require(userId.isNotBlank() && sessionId.isNotBlank())
        val encryptedAccess = cipher.seal(accessToken)
        val encryptedRefresh = cipher.seal(refreshToken)
        store.edit { prefs ->
            prefs[ACCESS_TOKEN] = encryptedAccess
            prefs[REFRESH_TOKEN] = encryptedRefresh
            prefs[USER_ID] = userId
            prefs[SESSION_ID] = sessionId
        }
    }

    suspend fun clearSession() {
        store.edit { prefs -> clearAuthenticationSession(prefs) }
    }

    override suspend fun getAccessToken(): String? = accessToken.first()
    suspend fun getRefreshToken(): String? = refreshToken.first()

    suspend fun getSession(): Session {
        val prefs = safeData.first()
        return Session(
            accessToken = cipher.open(prefs[ACCESS_TOKEN]),
            refreshToken = cipher.open(prefs[REFRESH_TOKEN]),
            userId = prefs[USER_ID],
            sessionId = prefs[SESSION_ID],
            deviceId = DeviceIdentityPreferences.read(prefs, prefs[USER_ID])
        )
    }

    override suspend fun getOrCreateInstallationId(): String {
        var id: String? = null
        store.edit { prefs -> id = InstallationIdentityPreferences.getOrCreate(prefs) }
        return checkNotNull(id)
    }

    override suspend fun getDeviceId(): String? {
        return try {
            var resolved: String? = null
            store.edit { prefs ->
                val id = prefs[USER_ID]?.trim()?.takeIf { it.isNotEmpty() }
                if (id != null) resolved = DeviceIdentityPreferences.migrateAndRead(prefs, id)
            }
            resolved
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun saveDeviceId(deviceId: String) {
        require(deviceId.isNotBlank())
        store.edit { prefs ->
            val user = requireNotNull(prefs[USER_ID]?.trim()?.takeIf { it.isNotEmpty() }) {
                "Cannot persist a device id without an authenticated user"
            }
            DeviceIdentityPreferences.save(prefs, user, deviceId)
        }
    }

    override suspend fun clearDeviceId() {
        store.edit { prefs ->
            prefs[USER_ID]?.trim()?.takeIf { it.isNotEmpty() }?.let { user ->
                DeviceIdentityPreferences.clear(prefs, user)
            }
        }
    }

    override suspend fun getTokenSnapshot(): TokenSnapshot? {
        val prefs = safeData.first()
        val access = cipher.open(prefs[ACCESS_TOKEN])?.takeIf { it.isNotBlank() }
        val refresh = cipher.open(prefs[REFRESH_TOKEN])?.takeIf { it.isNotBlank() }
        return if (access != null && refresh != null) TokenSnapshot(access, refresh) else null
    }

    override suspend fun updateTokens(accessToken: String, refreshToken: String) {
        require(accessToken.isNotBlank() && refreshToken.isNotBlank())
        val encryptedAccess = cipher.seal(accessToken)
        val encryptedRefresh = cipher.seal(refreshToken)
        store.edit { prefs ->
            prefs[ACCESS_TOKEN] = encryptedAccess
            prefs[REFRESH_TOKEN] = encryptedRefresh
        }
    }

    override suspend fun clearSessionIfMatches(expected: TokenSnapshot): Boolean {
        migrateLegacyTokens()
        var cleared = false
        store.edit { prefs ->
            val current = TokenSnapshot(
                accessToken = cipher.open(prefs[ACCESS_TOKEN]).orEmpty(),
                refreshToken = cipher.open(prefs[REFRESH_TOKEN]).orEmpty()
            )
            if (current == expected) {
                clearAuthenticationSession(prefs)
                cleared = true
            }
        }
        return cleared
    }

    private fun clearAuthenticationSession(prefs: MutablePreferences) {
        prefs.remove(ACCESS_TOKEN)
        prefs.remove(REFRESH_TOKEN)
        prefs.remove(USER_ID)
        prefs.remove(SESSION_ID)
        // Installation id + account-scoped server device ids survive logout by design.
    }

    suspend fun hasActiveSession(): Boolean = !getAccessToken().isNullOrBlank()
}
