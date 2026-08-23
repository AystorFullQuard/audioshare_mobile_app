package mme.corp.audioshare.data.api

import mme.corp.audioshare.data.dto.device.DeviceResponse
import mme.corp.audioshare.data.dto.device.RegisterDeviceRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

fun interface DevicesApi {

    @POST("/api/v1/devices")
    suspend fun registerDevice(
        @Body request: RegisterDeviceRequest
    ): Response<DeviceResponse>
}
