package com.saferenttechnologies.android.presentation.splash

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Loads the downloaded splash logo (already optimised to ≤768px PNG at download time),
 * downsampled close to this device's [sizePx] — so low-end / low-density phones decode
 * only what they need, high-density phones get the full sharp image.
 * Returns null if missing or unreadable → caller shows the bundled logo.
 * Blocking — call off the main thread.
 */
internal fun loadLogoBitmap(path: String, sizePx: Int): ImageBitmap? {
    val file = File(path).takeIf { it.isFile } ?: return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val target = sizePx.coerceAtLeast(1) * 2 // keep it sharp
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()?.asImageBitmap()
}
