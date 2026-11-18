package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Tenant's own ID documents (stored encrypted on the server) + sharing with the current landlord. */
interface DocumentsApi {
    @GET("api/v1/me/documents")
    suspend fun vault(): Response<ApiEnvelope<JsonElement>>

    /** type + 1–2 page photos ("files"). Same type again replaces it. */
    @Multipart
    @POST("api/v1/me/documents")
    suspend fun upload(@Part("type") type: RequestBody, @Part files: List<MultipartBody.Part>): Response<ApiEnvelope<JsonElement>>

    @DELETE("api/v1/me/documents/{id}")
    suspend fun delete(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    /** body { leaseId } — that lease's landlord can see it while the lease is active. */
    @POST("api/v1/me/documents/{id}/shares")
    suspend fun share(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @DELETE("api/v1/me/documents/{id}/shares/{leaseId}")
    suspend fun unshare(@Path("id") id: String, @Path("leaseId") leaseId: String): Response<ApiEnvelope<JsonElement>>

    /** Landlord: what this lease's tenant shared (403 once the lease has ended). */
    @GET("api/v1/leases/{leaseId}/documents")
    suspend fun shared(@Path("leaseId") leaseId: String): Response<ApiEnvelope<JsonElement>>
}
