package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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

    /** Every payment / refund, newest first. */
    @GET("api/v1/rent/transactions")
    suspend fun transactions(@Query("page") page: Int, @Query("pageSize") pageSize: Int): Response<ApiEnvelope<JsonElement>>

    /** The receipt as a PDF file (not the JSON envelope). */
    @Streaming
    @GET("api/v1/rent/receipts/{id}/pdf")
    suspend fun receiptPdf(@Path("id") id: String): Response<ResponseBody>
}
