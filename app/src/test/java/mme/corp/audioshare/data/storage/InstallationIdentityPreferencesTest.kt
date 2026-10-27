package mme.corp.audioshare.data.storage

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InstallationIdentityPreferencesTest {
    @Test fun idIsStableUntilAppDataIsCleared() {
        val preferences = mutablePreferencesOf()
        val first = InstallationIdentityPreferences.getOrCreate(preferences) { "first-install" }
        val afterLogout = InstallationIdentityPreferences.getOrCreate(preferences) {
            error("must not generate another ID")
        }
        assertEquals(first, afterLogout)
        val afterReinstall = InstallationIdentityPreferences.getOrCreate(mutablePreferencesOf()) {
            "second-install"
        }
        assertNotEquals(first, afterReinstall)
    }

    @Test fun emptyOrOversizedIdsAreNotStored() {
        assertThrows(IllegalArgumentException::class.java) {
            InstallationIdentityPreferences.getOrCreate(mutablePreferencesOf()) { " " }
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstallationIdentityPreferences.getOrCreate(mutablePreferencesOf()) { "a".repeat(129) }
        }
    }
}
