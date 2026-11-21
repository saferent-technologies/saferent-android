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

/** Safety & emergency: owner form + the tenant's Emergency screen. */
interface SafetyApi {
    @Headers("Cache-Control: no-cache")
    @GET("api/v1/properties/{id}/safety")
    suspend fun safety(@Path("id") propertyId: String): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/properties/{id}/safety")
    suspend fun saveSafety(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @Headers("Cache-Control: no-cache")
    @GET("api/v1/leases/{id}/emergency")
    suspend fun emergency(@Path("id") leaseId: String): Response<ApiEnvelope<JsonElement>>
}
