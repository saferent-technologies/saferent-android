package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PUT

interface UserLocationApi {
    /** body { latitude, longitude (≈1 km rounded), locality, city, state, pincode, role, source } */
    @PUT("api/v1/me/location")
    suspend fun put(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
