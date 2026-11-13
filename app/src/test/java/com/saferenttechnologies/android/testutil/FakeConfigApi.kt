package com.saferenttechnologies.android.testutil

import com.saferenttechnologies.android.data.remote.ConfigApi
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.util.Collections

typealias ConfigResponse = Response<ApiEnvelope<JsonObject>>

/** Records every request (the If-None-Match value sent) and answers with [responder]. */
class FakeConfigApi(
    var responder: suspend (etag: String?) -> ConfigResponse = {
        ApiResponses.ok(TestConfig.REAL_DATA_JSON, TestConfig.REAL_ETAG)
    },
) : ConfigApi {

    private val sentEtags = Collections.synchronizedList(mutableListOf<String?>())

    /** If-None-Match value of each request, in order. */
    val requests: List<String?> get() = synchronized(sentEtags) { sentEtags.toList() }
    val requestCount: Int get() = sentEtags.size

    override suspend fun config(etag: String?): ConfigResponse {
        sentEtags += etag
        return responder(etag)
    }
}

object ApiResponses {
    private val request = Request.Builder().url("http://localhost/api/v1/app/config").build()

    fun ok(dataJson: String, etag: String? = null): ConfigResponse {
        val data = SafeRentJson.instance.parseToJsonElement(dataJson).jsonObject
        val headers = Headers.Builder().apply { if (etag != null) add("ETag", etag) }.build()
        return Response.success(ApiEnvelope(success = true, message = "OK", data = data), headers)
    }

    /** 304 with an empty body — exactly what Retrofit hands back for Not Modified. */
    fun notModified(): ConfigResponse = Response.error(
        "".toResponseBody(),
        okhttp3.Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(304)
            .message("Not Modified")
            .build(),
    )

    fun error(code: Int, body: String): ConfigResponse =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))
}
