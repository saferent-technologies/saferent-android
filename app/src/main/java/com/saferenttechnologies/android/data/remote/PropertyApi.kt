package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.data.remote.dto.CompletenessDto
import com.saferenttechnologies.android.data.remote.dto.DocumentDataDto
import com.saferenttechnologies.android.data.remote.dto.ImagesDataDto
import com.saferenttechnologies.android.data.remote.dto.PropertyDataDto
import com.saferenttechnologies.android.data.remote.dto.PropertyTypesDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Add-property endpoints. Firebase ID token added by AuthInterceptor. */
interface PropertyApi {
    @GET("api/v1/property-types")
    suspend fun propertyTypes(): Response<ApiEnvelope<PropertyTypesDto>>

    /** data = { properties: [...] } (lenient: items / list also accepted). */
    @GET("api/v1/properties")
    suspend fun properties(@Query("publicationStatus") publicationStatus: String): Response<ApiEnvelope<JsonElement>>

    /** [view] = "draft" → the listing as changed so far (change ids + version) — what the wizard edits. */
    @GET("api/v1/properties/{id}")
    suspend fun property(@Path("id") id: String, @Query("view") view: String?): Response<ApiEnvelope<PropertyDataDto>>

    @POST("api/v1/properties")
    suspend fun create(@Body body: JsonObject): Response<ApiEnvelope<PropertyDataDto>>

    @PATCH("api/v1/properties/{id}")
    suspend fun patch(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<PropertyDataDto>>

    // ---- units (MULTI_UNIT, VILLA, SINGLE_UNIT, DUPLEX) ----
    @POST("api/v1/properties/{id}/units")
    suspend fun addUnit(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PATCH("api/v1/properties/{id}/units/{unitId}")
    suspend fun updateUnit(@Path("id") id: String, @Path("unitId") unitId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @DELETE("api/v1/properties/{id}/units/{unitId}")
    suspend fun deleteUnit(@Path("id") id: String, @Path("unitId") unitId: String): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/properties/{id}/units/{unitId}/floors")
    suspend fun saveFloors(@Path("id") id: String, @Path("unitId") unitId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/properties/{id}/units/{unitId}/pricing")
    suspend fun saveUnitPricing(@Path("id") id: String, @Path("unitId") unitId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    // ---- rooms & beds (PG / hostel / co-living) ----
    @POST("api/v1/properties/{id}/rooms")
    suspend fun addRoom(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PATCH("api/v1/properties/{id}/rooms/{roomId}")
    suspend fun updateRoom(@Path("id") id: String, @Path("roomId") roomId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/rooms/{roomId}/beds")
    suspend fun addBed(@Path("id") id: String, @Path("roomId") roomId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    // ---- rent, utilities, rules, amenities ----
    @PUT("api/v1/properties/{id}/pricing")
    suspend fun savePricing(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/properties/{id}/utilities")
    suspend fun saveUtilities(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/properties/{id}/policies")
    suspend fun savePolicies(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/amenities")
    suspend fun amenities(): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/properties/{id}/amenities")
    suspend fun saveAmenities(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    // ---- photos (camera only) ----
    @Multipart
    @POST("api/v1/properties/{id}/images")
    suspend fun uploadImages(
        @Path("id") id: String,
        @Part files: List<MultipartBody.Part>,
        @Part("category") category: RequestBody,
        @Part("slot") slot: RequestBody?,
        @Part("unitId") unitId: RequestBody?,
        @Part("roomId") roomId: RequestBody?,
    ): Response<ApiEnvelope<ImagesDataDto>>

    @DELETE("api/v1/properties/{id}/images/{imageId}")
    suspend fun deleteImage(@Path("id") id: String, @Path("imageId") imageId: String): Response<ApiEnvelope<JsonElement>>

    @PATCH("api/v1/properties/{id}/images/{imageId}")
    suspend fun updateImage(@Path("id") id: String, @Path("imageId") imageId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    // ---- ownership documents (camera only) ----
    @Multipart
    @POST("api/v1/properties/{id}/documents")
    suspend fun uploadDocument(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Part("docType") docType: RequestBody,
    ): Response<ApiEnvelope<DocumentDataDto>>

    /** data = { documents: [...] } (lenient). */
    @GET("api/v1/properties/{id}/documents")
    suspend fun documents(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    @Multipart
    @POST("api/v1/documents/{docId}/replace")
    suspend fun replaceDocument(@Path("docId") docId: String, @Part file: MultipartBody.Part): Response<ApiEnvelope<DocumentDataDto>>

    // ---- review ----
    @GET("api/v1/properties/{id}/completeness")
    suspend fun completeness(@Path("id") id: String, @Query("view") view: String?): Response<ApiEnvelope<CompletenessDto>>

    @POST("api/v1/properties/{id}/submit")
    suspend fun submit(@Path("id") id: String): Response<ApiEnvelope<PropertyDataDto>>
}
