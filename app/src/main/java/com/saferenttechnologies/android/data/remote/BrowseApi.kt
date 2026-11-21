package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/** Tenant: browse listings, apply, enquire, report, leases, applications, reviews. */
interface BrowseApi {
    @GET("api/v1/browse")
    suspend fun browse(@QueryMap query: Map<String, String>): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/browse/locations")
    suspend fun locations(@Query("q") q: String, @Query("limit") limit: Int): Response<ApiEnvelope<JsonElement>>

    /** Always fresh: videos / photos / availability change after SafeRent approves. */
    @retrofit2.http.Headers("Cache-Control: no-cache")
    @GET("api/v1/browse/{id}")
    suspend fun listing(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/browse/{id}/apply")
    suspend fun apply(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/browse/{id}/enquire")
    suspend fun enquire(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/fraud-reports")
    suspend fun report(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/properties/{id}/reviews")
    suspend fun reviews(
        @Path("id") id: String,
        @Query("pageSize") pageSize: Int,
        /** Past tenants' reviews of the landlord for THIS flat / bed (null = whole property). */
        @Query("unitId") unitId: String? = null,
        @Query("bedId") bedId: String? = null,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/leases")
    suspend fun leases(@Query("status") status: String, @Query("pageSize") pageSize: Int): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/reviews/pending")
    suspend fun pendingReviews(): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/applications")
    suspend fun applications(@Query("status") status: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/applications/{id}/withdraw")
    suspend fun withdraw(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>
}
