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
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Landlord announcements to tenants + "who saw it". */
interface AnnouncementsApi {
    /** audience ALL | PROPERTIES | TENANTS; propertyIds / leaseIds as repeated parts; image optional. */
    @Multipart
    @POST("api/v1/announcements")
    suspend fun create(
        @Part("title") title: RequestBody,
        @Part("body") body: RequestBody,
        @Part("audience") audience: RequestBody,
        @Part propertyIds: List<MultipartBody.Part>,
        @Part leaseIds: List<MultipartBody.Part>,
        @Part image: MultipartBody.Part?,
    ): Response<ApiEnvelope<JsonElement>>

    /** role=landlord → sent by me; role=tenant → sent to me. */
    @GET("api/v1/announcements")
    suspend fun list(@Query("role") role: String, @Query("page") page: Int, @Query("pageSize") pageSize: Int): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/announcements/{id}")
    suspend fun detail(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    /** Landlord: every recipient with viewedAt (null = not seen). */
    @GET("api/v1/announcements/{id}/recipients")
    suspend fun recipients(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    /** Tenant opened it (idempotent). */
    @POST("api/v1/announcements/{id}/view")
    suspend fun view(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
