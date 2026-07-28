package mme.corp.audioshare.data.storage

/**
 * Persists the ServeRelay device identifier returned by device bootstrap.
 */
interface DeviceIdStore {
    suspend fun getDeviceId(): String?

    suspend fun saveDeviceId(deviceId: String)
}
