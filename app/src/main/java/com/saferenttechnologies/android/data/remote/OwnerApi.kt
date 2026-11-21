package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/** Owner details (landlord profile) + property ownership. Private: no caching in logs. */
interface OwnerApi {
    @GET("api/v1/landlord/owner-details")
    suspend fun ownerDetails(): Response<ApiEnvelope<JsonElement>>

    /** Partial: only changed fields; null clears. */
    @PUT("api/v1/landlord/owner-details")
    suspend fun saveOwnerDetails(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/properties/{id}/ownership")
    suspend fun ownership(@Path("id") propertyId: String): Response<ApiEnvelope<JsonElement>>

    /** The full ownership object every time. */
    @PUT("api/v1/properties/{id}/ownership")
    suspend fun saveOwnership(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** Power of Attorney: "file" (JPG / PNG / PDF) + docType. */
    @Multipart
    @POST("api/v1/properties/{id}/documents")
    suspend fun uploadDocument(
        @Path("id") propertyId: String,
        @Part file: MultipartBody.Part,
        @Part("docType") docType: RequestBody,
        /** "YYYY-MM-DD" (Fire NOC, trade license …); null = not sent. */
        @Part("expiryDate") expiryDate: RequestBody? = null,
    ): Response<ApiEnvelope<JsonElement>>
}
