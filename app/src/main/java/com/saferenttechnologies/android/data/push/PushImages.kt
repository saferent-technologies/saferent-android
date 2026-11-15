package com.saferenttechnologies.android.data.push

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.saferenttechnologies.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Picture for a push (announcement image …). Our API's files need the signed-in token → [authed]
 * client; any other https host gets a plain client (the token never leaves our API).
 * Runs on FCM's background thread; gives up after a few seconds → the push shows without the picture.
 */
object PushImages {
    private const val MAX_SIDE = 1080
    private const val MAX_BYTES = 5L * 1024 * 1024

    private val plain by lazy { OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build() }

    /** Relative "/api/v1/files/…" → full API url; only https (or our API) is allowed. Pure, unit-tested. */
    fun resolve(url: String?, apiBase: String = BuildConfig.API_BASE_URL): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        val full = if (u.startsWith("http://") || u.startsWith("https://")) u else apiBase.trimEnd('/') + "/" + u.trimStart('/')
        val parsed = full.toHttpUrlOrNull() ?: return null
        return full.takeIf { parsed.isHttps || sameHost(full, apiBase) }
    }

    fun sameHost(url: String, apiBase: String = BuildConfig.API_BASE_URL): Boolean {
        val a = url.toHttpUrlOrNull() ?: return false
        val b = apiBase.toHttpUrlOrNull() ?: return false
        return a.host.equals(b.host, ignoreCase = true)
    }

    fun load(url: String?, authed: OkHttpClient): Bitmap? {
        val full = resolve(url) ?: return null
        val client = if (sameHost(full)) authed.newBuilder().callTimeout(6, TimeUnit.SECONDS).build() else plain
        return runCatching {
            client.newCall(Request.Builder().url(full).build()).execute().use { r ->
                if (!r.isSuccessful) return null
                val body = r.body ?: return null
                if (body.contentLength() > MAX_BYTES) return null
                val bytes = body.bytes()
                if (bytes.size > MAX_BYTES) return null
                decode(bytes)
            }
        }.getOrNull()
    }

    /** Down-sampled so a big photo doesn't blow the notification's memory limit. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
