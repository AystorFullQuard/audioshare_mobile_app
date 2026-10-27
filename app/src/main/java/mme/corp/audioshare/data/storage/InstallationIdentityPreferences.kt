package mme.corp.audioshare.data.storage

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID

/** An opaque, app-installation identifier; never a backend device UUID or Android hardware ID. */
interface InstallationIdStore {
    suspend fun getOrCreateInstallationId(): String
}

/** The caller must perform this inside one atomic DataStore edit. */
internal object InstallationIdentityPreferences {
    private val installationIdKey = stringPreferencesKey("installation_id")

    fun getOrCreate(
        preferences: MutablePreferences,
        newId: () -> String = { UUID.randomUUID().toString() }
    ): String {
        preferences[installationIdKey]?.let { existing ->
            require(existing.isNotBlank() && existing.length <= 128) {
                "Persisted installation identity is invalid"
            }
            return existing
        }
        val generated = newId()
        require(generated.isNotBlank() && generated.length <= 128) {
            "Generated installation identity is invalid"
        }
        preferences[installationIdKey] = generated
        return generated
    }
}
