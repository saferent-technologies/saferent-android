package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Society / association: owner form, parking slots, the lease's society card. */
interface SocietyApi {
    @Headers("Cache-Control: no-cache")
    @GET("api/v1/properties/{id}/society")
    suspend fun society(@Path("id") propertyId: String): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/properties/{id}/society")
    suspend fun saveSociety(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/parking-slots")
    suspend fun addSlot(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PATCH("api/v1/properties/{id}/parking-slots/{slotId}")
    suspend fun updateSlot(@Path("id") propertyId: String, @Path("slotId") slotId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @DELETE("api/v1/properties/{id}/parking-slots/{slotId}")
    suspend fun deleteSlot(@Path("id") propertyId: String, @Path("slotId") slotId: String): Response<ApiEnvelope<JsonElement>>

    /** Existing document PATCH: { "sharedWithTenant": true }. */
    @PATCH("api/v1/documents/{docId}")
    suspend fun updateDocument(@Path("docId") documentId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @Headers("Cache-Control: no-cache")
    @GET("api/v1/leases/{id}/society")
    suspend fun leaseSociety(@Path("id") leaseId: String): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/leases/{id}/society-approval")
    suspend fun saveApproval(@Path("id") leaseId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
