package com.saferenttechnologies.android.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.staticCompositionLocalOf
import com.saferenttechnologies.android.core.di.IoDispatcher
import com.saferenttechnologies.android.core.log.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Small image loader for server images (no third-party image library).
 * Downloads once into cacheDir (keyed by URL), decodes with the platform decoder,
 * downsampled to the size it is shown at. SVG / undecodable → null (caller falls back).
 */
@Singleton
class RemoteImageLoader @Inject constructor(
    @ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
    private val logger: AppLogger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val dir = File(context.cacheDir, "remote_images")

    suspend fun load(url: String, maxPx: Int): Bitmap? = withContext(ioDispatcher) {
        try {
            val file = cacheFile(url).takeIf { it.isFile } ?: download(url)
            decodeDownsampled(file, maxPx).also {
                if (it == null) logger.w(TAG, "Can't decode image (SVG / unsupported format): $url")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG, "Image load failed: $url", e)
            null
        }
    }

    private fun download(url: String): File {
        dir.mkdirs()
        val target = cacheFile(url)
        val part = File(dir, "${target.name}.part")
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IOException("Empty body for $url")
            body.byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        }
        if (!part.renameTo(target)) throw IOException("Couldn't cache $url")
        return target
    }

    private fun cacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, hash)
    }

    private fun decodeDownsampled(file: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val TAG = "SafeRent-Image"
    }
}

/** Provided in MainActivity; null in previews. */
val LocalRemoteImageLoader = staticCompositionLocalOf<RemoteImageLoader?> { null }
