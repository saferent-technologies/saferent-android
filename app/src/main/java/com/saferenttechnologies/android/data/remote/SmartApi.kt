package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Move-in vs move-out comparison, property / flat QR codes, smart rent reminders. */
interface SmartApi {
    @Headers("Cache-Control: no-cache")
    @GET("api/v1/leases/{id}/inspection-comparison")
    suspend fun comparison(@Path("id") leaseId: String): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/properties/{id}/qr")
    suspend fun propertyQr(@Path("id") propertyId: String): Response<ApiEnvelope<JsonElement>>

    @Headers("Cache-Control: no-cache")
    @GET("api/v1/qr/resolve")
    suspend fun resolve(@Query("code") code: String, @Query("unit") unit: String?): Response<ApiEnvelope<JsonElement>>

    @Headers("Cache-Control: no-cache")
    @GET("api/v1/rent/reminder-settings")
    suspend fun reminders(): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/rent/reminder-settings")
    suspend fun saveReminders(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
