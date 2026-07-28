package mme.corp.audioshare.testutil

import mme.corp.audioshare.data.storage.DeviceIdStore

class FakeDeviceIdStore(
    initialDeviceId: String? = null
) : DeviceIdStore {
    var storedDeviceId: String? = initialDeviceId
        private set

    override suspend fun getDeviceId(): String? = storedDeviceId

    override suspend fun saveDeviceId(deviceId: String) {
        storedDeviceId = deviceId
    }
}
