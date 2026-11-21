package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Tenant side of a landlord invitation (the /invite/{token} link). */
interface InvitationApi {
    /** Tenant: invitations sent to me (by my SafeRent account / phone), pending first. Each has its token. */
    @GET("api/v1/invitations/mine")
    suspend fun mine(): Response<ApiEnvelope<JsonElement>>

    @GET("api/v1/invitations/token/{token}")
    suspend fun byToken(@Path("token") token: String): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/invitations/token/{token}/accept")
    suspend fun accept(@Path("token") token: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/invitations/token/{token}/reject")
    suspend fun reject(@Path("token") token: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
