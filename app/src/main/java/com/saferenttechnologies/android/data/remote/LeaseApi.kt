package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** One lease (tenant or landlord): detail, mutual close ("exit") requests, review. */
interface LeaseApi {
    @GET("api/v1/leases/{id}")
    suspend fun lease(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/leases/{id}/exit")
    suspend fun exit(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/leases/{id}/exit")
    suspend fun requestExit(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/leases/{id}/exit/accept")
    suspend fun acceptExit(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/leases/{id}/exit/decline")
    suspend fun declineExit(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/leases/{id}/exit/cancel")
    suspend fun cancelExit(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** Closed leases I still have to review (tenant: before using the app; landlord: before the next invite). */
    @GET("api/v1/reviews/pending")
    suspend fun pendingReviews(): Response<ApiEnvelope<JsonElement>>

    /** Tenant: was the security deposit refunded? */
    @POST("api/v1/leases/{id}/deposit-refund")
    suspend fun depositRefund(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/leases/{id}/review")
    suspend fun review(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/leases/{id}/review")
    suspend fun postReview(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
