package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/** Tenant "Open to new homes" alert (one per user). */
interface HomeAlertsApi {
    @GET("api/v1/me/home-alert")
    suspend fun get(): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/me/home-alert")
    suspend fun save(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
