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

/** Help & support tickets (to SafeRent). */
interface SupportApi {
    @POST("api/v1/support/tickets")
    suspend fun create(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/support/tickets")
    suspend fun list(
        @Query("status") status: String?,
        @Query("q") q: String?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/support/tickets/{id}")
    suspend fun detail(@Path("id") id: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/support/tickets/{id}/messages")
    suspend fun reply(@Path("id") id: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
