package com.saferenttechnologies.android.data.splash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.saferenttechnologies.android.core.log.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and stores splash assets under `filesDir/splash_assets/<version>-<time>/`.
 * Each version gets its own folder, so the old splash keeps working until the new one is
 * fully downloaded and verified. Blocking — call from an IO dispatcher.
 */
@Singleton
class SplashAssetFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val json: Json,
    private val logger: AppLogger,
) {
    private val client = okHttpClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val root: File get() = File(context.filesDir, ROOT_DIR)

    fun newVersionDir(version: String): File {
        val safe = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(root, "$safe-${System.currentTimeMillis()}").apply { mkdirs() }
    }

    /**
     * Downloads a logo and stores a device-ready copy as `<name>.png`.
     *
     * The server sends a high-quality image (png / jpg / webp / heif / avif …). Here we
     * decode it with the platform decoder, downsample it to at most [MAX_LOGO_PX] px
     * (enough for xxxhdpi at the splash logo size) and re-save it as PNG (lossless,
     * keeps transparency, decodes on every Android version). The original is deleted.
     *
     * Returns null → the app's bundled logo is used, when the logo is SVG (no native
     * support) or this device can't decode the format (e.g. AVIF on older Android).
     */
    fun downloadLogo(url: String, dir: File, name: String): File? {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
            .takeIf { it.isNotBlank() && it.length <= 5 } ?: "img"
        if (ext == "svg") {
            logger.w(TAG, "SVG logo not supported natively — using app logo: $url")
            return null
        }
        val original = download(url, File(dir, "$name-original.$ext"))
        try {
            if (original.looksLikeSvg()) {
                logger.w(TAG, "Logo is SVG content — not supported, using app logo: $url")
                return null
            }
            val bitmap = decodeDownsampled(original, MAX_LOGO_PX)
            if (bitmap == null) {
                logger.w(TAG, "This device can't decode the logo format — using app logo: $url")
                return null
            }
            val logo = File(dir, "$name.png")
            try {
                logo.outputStream().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("Couldn't save logo.png")
                    }
                }
            } finally {
                bitmap.recycle()
            }
            logger.d(TAG, "Logo optimised: ${original.length()} → ${logo.length()} bytes (${MAX_LOGO_PX}px max)")
            return logo
        } finally {
            original.delete()
        }
    }

    /**
     * Decodes [file] without ever loading the full-size image into memory:
     * power-of-two sampling first, then an exact filtered scale so the longest
     * side is at most [maxPx]. Returns null if the format can't be decoded.
     */
    private fun decodeDownsampled(file: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxPx) sample *= 2
        val sampled = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888 // keep transparency & quality
            },
        ) ?: return null

        val longest = maxOf(sampled.width, sampled.height)
        if (longest <= maxPx) return sampled
        val scale = maxPx.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            sampled,
            (sampled.width * scale).toInt().coerceAtLeast(1),
            (sampled.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != sampled) sampled.recycle()
        return scaled
    }

    private fun File.looksLikeSvg(): Boolean {
        val head = inputStream().use { input ->
            val buffer = ByteArray(512)
            val read = input.read(buffer)
            if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8)
        }
        return head.contains("<svg", ignoreCase = true)
    }

    /** Downloads the Lottie JSON and verifies it looks like a Lottie animation. */
    fun downloadLottie(url: String, dir: File): File {
        val file = download(url, File(dir, "loading.json"))
        val root = json.parseToJsonElement(file.readText()).jsonObject // throws if not JSON
        require("layers" in root && "v" in root) { "Not a Lottie animation: $url" }
        return file
    }

    /** Permanently deletes every splash asset folder except [keep]. */
    fun deleteAllExcept(keep: File?) {
        root.listFiles()?.filter { it != keep }?.forEach { old ->
            if (old.deleteRecursively()) logger.d(TAG, "Deleted old splash assets: ${old.name}")
        }
    }

    private fun download(url: String, target: File): File {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IOException("Empty body for $url")
            if (body.contentLength() > MAX_BYTES) throw IOException("File too large: $url")
            val part = File(target.parentFile, "${target.name}.part")
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MAX_BYTES) throw IOException("File too large: $url")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Couldn't save ${target.name}")
        }
        logger.d(TAG, "Downloaded ${target.name} (${target.length()} bytes) ← $url")
        return target
    }

    private companion object {
        const val TAG = "SafeRent-Splash"
        const val ROOT_DIR = "splash_assets"
        const val MAX_BYTES = 20L * 1024 * 1024

        /** 112dp splash logo × 4 (xxxhdpi) = 448px; 768 leaves headroom for bigger layouts. */
        const val MAX_LOGO_PX = 768
    }
}
