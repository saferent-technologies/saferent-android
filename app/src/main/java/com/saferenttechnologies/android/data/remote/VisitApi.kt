package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.QueryMap

/** Property visits: availability (owner), slots + booking (tenant), host actions. Never logs notes / phones. */
interface VisitApi {
    @Headers("Cache-Control: no-cache")
    @GET("api/v1/properties/{id}/visit-settings")
    suspend fun settings(@Path("id") propertyId: String): Response<ApiEnvelope<JsonElement>>

    @PUT("api/v1/properties/{id}/visit-settings")
    suspend fun saveSettings(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @Headers("Cache-Control: no-cache")
    @GET("api/v1/browse/{id}/visit-slots")
    suspend fun slots(@Path("id") propertyId: String, @QueryMap query: Map<String, String>): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/browse/{id}/visits")
    suspend fun book(@Path("id") propertyId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @Headers("Cache-Control: no-cache")
    @GET("api/v1/my/visits")
    suspend fun myVisits(@QueryMap query: Map<String, String>): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/my/visits/{id}/cancel")
    suspend fun cancelMine(@Path("id") visitId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/my/visits/{id}/feedback")
    suspend fun feedback(@Path("id") visitId: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** Host / owner / manager: only visits the caller hosts or manages. */
    @Headers("Cache-Control: no-cache")
    @GET("api/v1/visits")
    suspend fun hostVisits(@QueryMap query: Map<String, String>): Response<ApiEnvelope<JsonElement>>

    /** action = confirm | reject | cancel | complete | no-show | host */
    @POST("api/v1/visits/{id}/{action}")
    suspend fun hostAction(@Path("id") visitId: String, @Path("action") action: String, @Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
