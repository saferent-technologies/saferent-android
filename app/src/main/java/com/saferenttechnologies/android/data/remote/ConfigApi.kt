package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/** Public endpoint (no login). Base URL = BuildConfig.API_BASE_URL. */
interface ConfigApi {
    /**
     * @param etag saved ETag; when unchanged the server answers 304 with an empty body.
     *             null → header is not sent.
     */
    @GET("api/v1/app/config")
    suspend fun config(
        @Header("If-None-Match") etag: String?,
    ): Response<ApiEnvelope<JsonObject>>
}
