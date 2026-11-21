package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Streaming
import retrofit2.http.Url
import retrofit2.http.Path
import retrofit2.http.Query

/** Tenant requests (maintenance / service / complaint) and the landlord's updates. */
interface RequestsApi {
    @GET("api/v1/requests/options")
    suspend fun options(): Response<ApiEnvelope<JsonElement>>

    @Multipart
    @POST("api/v1/requests")
    suspend fun create(
        @Part("type") type: RequestBody,
        @Part("category") category: RequestBody,
        @Part("description") description: RequestBody,
        @Part("priority") priority: RequestBody,
        @Part("leaseId") leaseId: RequestBody?,
        @Part files: List<MultipartBody.Part>,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/requests")
    suspend fun list(
        @Query("status") status: String?,
        @Query("role") role: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/requests/summary")
    suspend fun summary(): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/requests/{id}")
    suspend fun detail(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/requests/{id}/remind")
    suspend fun remind(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/requests/{id}/resolve")
    suspend fun resolve(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** Resolve with receipt photos / PDFs (fields as text parts). */
    @Multipart
    @POST("api/v1/requests/{id}/resolve")
    suspend fun resolveWithFiles(@Path("id") id: String, @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>, @Part files: List<MultipartBody.Part>): Response<ApiEnvelope<JsonElement>>

    @Multipart
    @POST("api/v1/requests/{id}/invoice")
    suspend fun addInvoice(@Path("id") id: String, @Part files: List<MultipartBody.Part>): Response<ApiEnvelope<JsonElement>>

    /** A request file (invoice PDF); relative url, the signed-in token is added like any call. */
    @Streaming
    @GET
    suspend fun file(@Url url: String): Response<ResponseBody>

    @POST("api/v1/requests/{id}/cancel")
    suspend fun cancel(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @Multipart
    @POST("api/v1/requests/{id}/reopen")
    suspend fun reopen(@Path("id") id: String, @Part("remark") remark: RequestBody, @Part files: List<MultipartBody.Part>): Response<ApiEnvelope<JsonElement>>

    @Multipart
    @POST("api/v1/requests/{id}/photos")
    suspend fun addPhotos(@Path("id") id: String, @Part files: List<MultipartBody.Part>): Response<ApiEnvelope<JsonElement>>

    /** Update without files: JSON (null clears technician / estimate / start date). */
    @POST("api/v1/requests/{id}/update")
    suspend fun update(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @Multipart
    @POST("api/v1/requests/{id}/update")
    suspend fun updateWithFiles(
        @Path("id") id: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part files: List<MultipartBody.Part>,
    ): Response<ApiEnvelope<JsonElement>>
}
