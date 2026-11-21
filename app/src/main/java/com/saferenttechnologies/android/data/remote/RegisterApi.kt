package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.QueryMap

/** Furniture & appliances register of a property. Private: never logged. */
interface RegisterApi {
    /** query: view=draft, unitId, roomId (all optional). Always fresh. */
    @Headers("Cache-Control: no-cache")
    @GET("api/v1/properties/{id}/inventory")
    suspend fun list(@Path("id") propertyId: String, @QueryMap query: Map<String, String>): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/inventory")
    suspend fun create(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PATCH("api/v1/properties/{id}/inventory/{itemId}")
    suspend fun update(@Path("id") propertyId: String, @Path("itemId") itemId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @DELETE("api/v1/properties/{id}/inventory/{itemId}")
    suspend fun delete(@Path("id") propertyId: String, @Path("itemId") itemId: String): Response<ApiEnvelope<JsonElement>>

    @Multipart
    @POST("api/v1/properties/{id}/inventory/{itemId}/photos")
    suspend fun uploadPhotos(@Path("id") propertyId: String, @Path("itemId") itemId: String, @Part files: List<MultipartBody.Part>): Response<ApiEnvelope<JsonElement>>

    @DELETE("api/v1/properties/{id}/inventory/{itemId}/photos/{photoId}")
    suspend fun deletePhoto(@Path("id") propertyId: String, @Path("itemId") itemId: String, @Path("photoId") photoId: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/inventory/{itemId}/repairs")
    suspend fun addRepair(@Path("id") propertyId: String, @Path("itemId") itemId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PATCH("api/v1/properties/{id}/inventory/{itemId}/repairs/{repairId}")
    suspend fun updateRepair(@Path("id") propertyId: String, @Path("itemId") itemId: String, @Path("repairId") repairId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @DELETE("api/v1/properties/{id}/inventory/{itemId}/repairs/{repairId}")
    suspend fun deleteRepair(@Path("id") propertyId: String, @Path("itemId") itemId: String, @Path("repairId") repairId: String): Response<ApiEnvelope<JsonElement>>
}
