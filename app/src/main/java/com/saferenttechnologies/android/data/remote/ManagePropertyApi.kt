package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.data.remote.dto.PropertyDataDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/** Landlord: dashboard summary, my properties, lifecycle actions, tenants, invitations, applications. */
interface ManagePropertyApi {
    @GET("api/v1/properties/summary")
    suspend fun summary(): Response<ApiEnvelope<JsonElement>>

    /** data = array, paging in meta. */
    @GET("api/v1/properties")
    suspend fun list(@QueryMap query: Map<String, String>): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/properties/{id}")
    suspend fun detail(@Path("id") id: String): Response<ApiEnvelope<PropertyDataDto>>

    /** Moderator decision: { decision: "APPROVE" | "REJECT", withoutOwnershipDocument?, reason? }. */
    @POST("api/v1/properties/{id}/review")
    suspend fun review(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/publish")
    suspend fun publish(@Path("id") id: String): Response<ApiEnvelope<PropertyDataDto>>

    @POST("api/v1/properties/{id}/unpublish")
    suspend fun unpublish(@Path("id") id: String): Response<ApiEnvelope<PropertyDataDto>>

    @POST("api/v1/properties/{id}/status")
    suspend fun setActiveStatus(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<PropertyDataDto>>

    /** Draft → deleted (or archived when it has activity); otherwise archived. */
    @DELETE("api/v1/properties/{id}")
    suspend fun archiveOrDelete(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/submit")
    suspend fun submit(@Path("id") id: String): Response<ApiEnvelope<PropertyDataDto>>

    /** Change request (live listing): send for verification. data = { pendingRevision }. */
    @POST("api/v1/properties/{id}/revision/submit")
    suspend fun submitRevision(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    /** Change request: throw away. data = { pendingRevision: null }. */
    @DELETE("api/v1/properties/{id}/revision")
    suspend fun discardRevision(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    @PATCH("api/v1/properties/{id}/units/{unitId}")
    suspend fun updateUnit(@Path("id") id: String, @Path("unitId") unitId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @PATCH("api/v1/properties/{id}/rooms/{roomId}/beds/{bedId}")
    suspend fun updateBed(
        @Path("id") id: String,
        @Path("roomId") roomId: String,
        @Path("bedId") bedId: String,
        @Body body: JsonObject,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/leases")
    suspend fun leases(@Query("propertyId") propertyId: String?, @Query("status") status: String, @Query("page") page: Int): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/invitations")
    suspend fun invitations(@Query("propertyId") propertyId: String, @Query("status") status: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/properties/{id}/invitations")
    suspend fun createInvitation(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/invitations/{invId}/link")
    suspend fun renewInvitationLink(@Path("invId") invitationId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** Owner: what the tenant answered when accepting. Private: never logged. */
    @retrofit2.http.Headers("Cache-Control: no-cache")
    @GET("api/v1/invitations/{invId}/tenant-details")
    suspend fun tenantDetails(@Path("invId") invitationId: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/invitations/{invId}/revoke")
    suspend fun revokeInvitation(@Path("invId") invitationId: String): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/applications")
    suspend fun applications(@Query("propertyId") propertyId: String, @Query("status") status: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/applications/{appId}/decision")
    suspend fun decideApplication(@Path("appId") applicationId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/properties/{id}/status-history")
    suspend fun statusHistory(@Path("id") id: String, @Query("page") page: Int): Response<ApiEnvelope<JsonElement>>
}
