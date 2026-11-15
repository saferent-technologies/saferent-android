package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap

interface NotificationApi {
    /** page, pageSize (+ unread=true). data = array, meta = { page, pageSize, total, totalPages, unread }. */
    @GET("api/v1/notifications")
    suspend fun list(@QueryMap query: Map<String, String>): Response<ApiEnvelope<JsonElement>>

    /** { "id": "…" } → one; { } → all. data = null. */
    @POST("api/v1/notifications/read")
    suspend fun markRead(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
