package mme.corp.audioshare.testutil

import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

fun MockWebServer.retrofit(): Retrofit = Retrofit.Builder()
    .baseUrl(url("/"))
    .addConverterFactory(GsonConverterFactory.create())
    .build()
