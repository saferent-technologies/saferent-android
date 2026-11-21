package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** India compliance: property (declaration, tax, approval, documents) and lease (agreement, police verification). */
interface ComplianceApi {
    @Headers("Cache-Control: no-cache")
    @GET("api/v1/properties/{id}/compliance")
    suspend fun compliance(@Path("id") propertyId: String): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/properties/{id}/compliance")
    suspend fun saveCompliance(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/ownership-declaration")
    suspend fun declare(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @Headers("Cache-Control: no-cache")
    @GET("api/v1/leases/{id}/compliance")
    suspend fun leaseCompliance(@Path("id") leaseId: String): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/leases/{id}/compliance")
    suspend fun saveLeaseCompliance(@Path("id") leaseId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
