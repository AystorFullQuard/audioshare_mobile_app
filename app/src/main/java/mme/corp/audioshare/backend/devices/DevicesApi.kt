package mme.corp.audioshare.backend.devices

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface DevicesApi {
    @GET("api/v1/devices")
    suspend fun getDevices(): Response<List<DeviceResponse>>

    @DELETE("api/v1/devices/{deviceId}")
    suspend fun deleteDevice(
        @Path("deviceId") deviceId: String
    ): Response<Unit>
}
