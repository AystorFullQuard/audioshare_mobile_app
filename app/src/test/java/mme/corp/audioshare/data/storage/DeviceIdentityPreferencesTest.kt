package mme.corp.audioshare.data.storage

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceIdentityPreferencesTest {

    @Test
    fun legacyDeviceIdMigratesToCurrentUserAndIsRemovedFromGlobalKey() {
        val preferences = mutablePreferencesOf(
            DeviceIdentityPreferences.legacyDeviceIdKey to LEGACY_DEVICE_ID
        )

        val resolved = DeviceIdentityPreferences.migrateAndRead(
            preferences = preferences,
            userId = USER_ID
        )

        assertEquals(LEGACY_DEVICE_ID, resolved)
        assertNull(preferences[DeviceIdentityPreferences.legacyDeviceIdKey])
        assertEquals(
            LEGACY_DEVICE_ID,
            DeviceIdentityPreferences.read(preferences, USER_ID)
        )
        assertNull(DeviceIdentityPreferences.read(preferences, SECOND_USER_ID))
    }

    @Test
    fun existingScopedDeviceWinsOverLegacyFallback() {
        val preferences = mutablePreferencesOf(
            DeviceIdentityPreferences.legacyDeviceIdKey to LEGACY_DEVICE_ID
        )

        DeviceIdentityPreferences.save(
            preferences = preferences,
            userId = USER_ID,
            deviceId = SCOPED_DEVICE_ID
        )

        assertEquals(
            SCOPED_DEVICE_ID,
            DeviceIdentityPreferences.migrateAndRead(preferences, USER_ID)
        )
        assertEquals(
            LEGACY_DEVICE_ID,
            preferences[DeviceIdentityPreferences.legacyDeviceIdKey]
        )
    }

    @Test
    fun explicitInvalidationRemovesLegacyFallbackWhenNoScopedMappingExists() {
        val preferences = mutablePreferencesOf(
            DeviceIdentityPreferences.legacyDeviceIdKey to LEGACY_DEVICE_ID
        )

        DeviceIdentityPreferences.clear(
            preferences = preferences,
            userId = USER_ID
        )

        assertNull(DeviceIdentityPreferences.read(preferences, USER_ID))
        assertNull(preferences[DeviceIdentityPreferences.legacyDeviceIdKey])
    }

    @Test
    fun explicitInvalidationRemovesLegacyFallbackWithoutTouchingAnotherUsersScopedDevice() {
        val preferences = mutablePreferencesOf(
            DeviceIdentityPreferences.legacyDeviceIdKey to LEGACY_DEVICE_ID
        )

        DeviceIdentityPreferences.save(preferences, USER_ID, SCOPED_DEVICE_ID)
        DeviceIdentityPreferences.save(
            preferences,
            SECOND_USER_ID,
            SECOND_SCOPED_DEVICE_ID
        )

        DeviceIdentityPreferences.clear(preferences, USER_ID)

        assertNull(DeviceIdentityPreferences.read(preferences, USER_ID))
        assertNull(preferences[DeviceIdentityPreferences.legacyDeviceIdKey])
        assertEquals(
            SECOND_SCOPED_DEVICE_ID,
            DeviceIdentityPreferences.read(preferences, SECOND_USER_ID)
        )
    }

    private companion object {
        const val USER_ID = "user-id"
        const val SECOND_USER_ID = "user-id-two"
        const val LEGACY_DEVICE_ID = "legacy-device-id"
        const val SCOPED_DEVICE_ID = "scoped-device-id"
        const val SECOND_SCOPED_DEVICE_ID = "scoped-device-id-two"
    }
}
