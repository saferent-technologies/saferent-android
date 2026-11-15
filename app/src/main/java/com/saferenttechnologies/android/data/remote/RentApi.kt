package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Landlord rent: this month's dues by bucket, receipts (PDF), reminders. */
interface RentApi {
    @GET("api/v1/rent/dues")
    suspend fun dues(@Query("status") status: String, @Query("period") period: String?): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/rent/dues/{id}/remind")
    suspend fun remind(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** Tenant: deposit + monthly rent of one lease. */
    @GET("api/v1/leases/{id}/payments")
    suspend fun leasePayments(@Path("id") leaseId: String): Response<ApiEnvelope<JsonElement>>

    /** Tenant: "I paid this month" with the transaction id + a screenshot. */
    @Multipart
    @POST("api/v1/rent/dues/{id}/payment-requests")
    suspend fun requestPaymentUpdate(
        @Path("id") dueId: String,
        @Part("amount") amount: RequestBody,
        @Part("transactionId") transactionId: RequestBody,
        @Part proof: MultipartBody.Part,
    ): Response<ApiEnvelope<JsonElement>>

    /** Tenant: pay this month from the security deposit (the landlord approves it like a proof). */
    @POST("api/v1/rent/dues/{id}/use-deposit")
    suspend fun useDeposit(@Path("id") dueId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** Landlord: tenants' payment proofs waiting for approval. */
    @GET("api/v1/rent/payment-requests")
    suspend fun paymentRequests(@Query("status") status: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/rent/payment-requests/{id}/approve")
    suspend fun approveRequest(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/rent/payment-requests/{id}/reject")
    suspend fun rejectRequest(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** Landlord: security deposit received (amount + date). */
    @POST("api/v1/leases/{id}/deposit")
    suspend fun recordDeposit(@Path("id") leaseId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** Every payment / refund, newest first. */
    @GET("api/v1/rent/transactions")
    suspend fun transactions(@Query("page") page: Int, @Query("pageSize") pageSize: Int): Response<ApiEnvelope<JsonElement>>

    /** The receipt as a PDF file (not the JSON envelope). */
    @Streaming
    @GET("api/v1/rent/receipts/{id}/pdf")
    suspend fun receiptPdf(@Path("id") id: String): Response<ResponseBody>
}
