package com.saferenttechnologies.android.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * In-memory cache of our API's GET responses, so data loaded once is NOT fetched again when a
 * screen is reopened / resumed. The network is used again only when:
 * - the user pulls to refresh ([clear] from the screen's Refresh),
 * - the app changes something (any POST / PUT / PATCH / DELETE clears everything),
 * - a push arrives (someone else changed something), or on sign-out / app restart.
 * Only JSON, only 2xx, only small bodies. Files (photos, PDFs), ID documents, config / splash and auth calls
 * always go to the network.
 */
object ApiCache {
    private const val MAX_ENTRIES = 150
    private const val MAX_BYTES = 1024 * 1024
    private val SKIP = listOf("/config", "/splash", "/files/", "/pdf", "/auth", "/phone/", "/devices", "/fcm", "/documents", "/users/lookup", "/tenants/report", "/kyc")

    private class Entry(val code: Int, val message: String, val contentType: String?, val body: ByteArray, val headers: okhttp3.Headers)

    // Access-ordered LinkedHashMap = LRU.
    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > MAX_ENTRIES
    }

    /** Pull-to-refresh / data changed → everything loads fresh next time. */
    @Synchronized fun clear() = entries.clear()

    @Synchronized private fun get(key: String) = entries[key]
    @Synchronized private fun put(key: String, e: Entry) { entries[key] = e }

    /** Pure rule (unit-tested): which GETs may be cached. */
    fun cacheable(method: String, path: String): Boolean =
        method.equals("GET", true) && path.contains("/api/") && SKIP.none { path.contains(it, ignoreCase = true) }

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val method = request.method
        if (!method.equals("GET", true)) {
            // Something is being changed → whatever we kept may be out of date.
            clear()
            return@Interceptor chain.proceed(request)
        }
        val path = request.url.encodedPath
        if (!cacheable(method, path) || request.header("Cache-Control")?.contains("no-cache") == true) {
            return@Interceptor chain.proceed(request)
        }
        val key = request.url.toString()
        get(key)?.let { e ->
            return@Interceptor Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(e.code)
                .message(e.message)
                .headers(e.headers)
                .body(e.body.toResponseBody(e.contentType?.toMediaTypeOrNull()))
                .build()
        }
        val response = chain.proceed(request)
        val type: MediaType? = response.body?.contentType()
        val json = type?.subtype?.contains("json", ignoreCase = true) == true
        val length = response.body?.contentLength() ?: -1L
        if (!response.isSuccessful || !json || length > MAX_BYTES) return@Interceptor response
        val bytes = response.body?.bytes() ?: return@Interceptor response
        if (bytes.size <= MAX_BYTES) put(key, Entry(response.code, response.message, type.toString(), bytes, response.headers))
        // The body was read → hand back a fresh copy.
        response.newBuilder().body(bytes.toResponseBody(type)).build()
    }
}
