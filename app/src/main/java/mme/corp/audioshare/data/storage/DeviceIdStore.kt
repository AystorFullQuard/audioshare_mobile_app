package mme.corp.audioshare.data.storage

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Persists the ServeRelay device identifier registered for the current user.
 */
interface DeviceIdStore {
    suspend fun getDeviceId(): String?

    suspend fun saveDeviceId(deviceId: String)

    suspend fun clearDeviceId()
}

/**
 * User-scoped device identity stored inside the existing session DataStore.
 *
 * The legacy global `device_id` key is migrated lazily to the first authenticated
 * user that resolves it after upgrade. If that legacy id belongs to another
 * account, the later bootstrap recovery stage will reject and replace it using
 * ServeRelay's ownership error codes.
 */
internal object DeviceIdentityPreferences {
    private const val DEVICE_ID_PREFIX = "device_id:"

    internal val legacyDeviceIdKey =
        stringPreferencesKey("device_id")

    fun read(
        preferences: Preferences,
        userId: String?
    ): String? {
        val normalizedUserId = userId.normalizedRequiredOrNull()
            ?: return null

        val scoped = preferences[deviceIdKey(normalizedUserId)]
            .normalizedRequiredOrNull()

        if (scoped != null) {
            return scoped
        }

        return preferences[legacyDeviceIdKey]
            .normalizedRequiredOrNull()
    }

    fun migrateAndRead(
        preferences: MutablePreferences,
        userId: String
    ): String? {
        val normalizedUserId = requireNormalized(userId, "User id")
        val scopedKey = deviceIdKey(normalizedUserId)
        val scoped = preferences[scopedKey].normalizedRequiredOrNull()

        if (scoped != null) {
            return scoped
        }

        val legacy = preferences[legacyDeviceIdKey]
            .normalizedRequiredOrNull()
            ?: return null

        preferences[scopedKey] = legacy
        preferences.remove(legacyDeviceIdKey)

        return legacy
    }

    fun save(
        preferences: MutablePreferences,
        userId: String,
        deviceId: String
    ) {
        val normalizedUserId = requireNormalized(userId, "User id")
        val normalizedDeviceId = requireNormalized(deviceId, "Device id")

        preferences[deviceIdKey(normalizedUserId)] = normalizedDeviceId
    }

    fun clear(
        preferences: MutablePreferences,
        userId: String
    ) {
        val normalizedUserId = requireNormalized(userId, "User id")

        // Explicit invalidation must remove both the current user's scoped
        // mapping and the legacy fallback. Otherwise a stale legacy value can
        // be resolved again immediately after the scoped mapping is cleared.
        preferences.remove(deviceIdKey(normalizedUserId))
        preferences.remove(legacyDeviceIdKey)
    }

    private fun deviceIdKey(userId: String): Preferences.Key<String> =
        stringPreferencesKey("$DEVICE_ID_PREFIX$userId")

    private fun requireNormalized(
        value: String,
        fieldName: String
    ): String = value.trim().also { normalized ->
        require(normalized.isNotEmpty()) { "$fieldName must not be blank" }
    }

    private fun String?.normalizedRequiredOrNull(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)
}
