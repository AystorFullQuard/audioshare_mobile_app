package mme.corp.audioshare.testutil

import mme.corp.audioshare.data.storage.DeviceIdStore
import mme.corp.audioshare.data.storage.InstallationIdStore

class FakeDeviceIdStore(
    initialDeviceId: String? = null
) : DeviceIdStore, InstallationIdStore {
    var storedInstallationId: String = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

    override suspend fun getOrCreateInstallationId(): String = storedInstallationId

    var storedDeviceId: String? = initialDeviceId
        private set

    override suspend fun getDeviceId(): String? = storedDeviceId

    override suspend fun saveDeviceId(deviceId: String) {
        storedDeviceId = deviceId
    }

    override suspend fun clearDeviceId() {
        storedDeviceId = null
    }
}
