package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Property inspection reports (owner, manager, inspector). Private: never logged. */
interface InspectionApi {
    /** Always fresh (reports change from other phones / the portal). */
    @Headers("Cache-Control: no-cache")
    @GET("api/v1/properties/{id}/inspections")
    suspend fun list(@Path("id") propertyId: String, @Query("page") page: Int, @Query("pageSize") pageSize: Int): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/inspections")
    suspend fun create(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PATCH("api/v1/properties/{id}/inspections/{inspectionId}")
    suspend fun update(@Path("id") propertyId: String, @Path("inspectionId") inspectionId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/inspections/{inspectionId}/acknowledge")
    suspend fun acknowledge(@Path("id") propertyId: String, @Path("inspectionId") inspectionId: String): Response<ApiEnvelope<JsonElement>>

    /** Inspection photos: "files" (1–10) + inspectionId + category. */
    @Multipart
    @POST("api/v1/properties/{id}/images")
    suspend fun uploadPhotos(
        @Path("id") propertyId: String,
        @Part files: List<MultipartBody.Part>,
        @Part("inspectionId") inspectionId: RequestBody,
        @Part("category") category: RequestBody,
    ): Response<ApiEnvelope<JsonElement>>
}
