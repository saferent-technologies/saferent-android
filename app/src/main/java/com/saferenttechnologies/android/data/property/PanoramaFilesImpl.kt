package com.saferenttechnologies.android.data.property

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.saferenttechnologies.android.domain.model.PanoramaRules
import com.saferenttechnologies.android.domain.repository.PanoramaFiles
import com.saferenttechnologies.android.domain.repository.PanoramaPrep
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Reads a picked panorama, checks it's wide enough, scales to ≤ 4096 × 2048, re-encodes (drops EXIF / GPS). */
@Singleton
class PanoramaFilesImpl @Inject constructor(@ApplicationContext private val context: Context) : PanoramaFiles {
    override fun prepare(uri: String): PanoramaPrep = runCatching {
        val u = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return PanoramaPrep.Unreadable
        if (!PanoramaRules.isPanorama(w, h)) return PanoramaPrep.NotPanorama
        val (tw, th) = PanoramaRules.scaledSize(w, h)
        var sample = 1
        while (w / (sample * 2) >= tw && h / (sample * 2) >= th) sample *= 2
        val decoded = context.contentResolver.openInputStream(u)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return PanoramaPrep.Unreadable
        val out = if (decoded.width != tw || decoded.height != th) Bitmap.createScaledBitmap(decoded, tw, th, true) else decoded
        if (out !== decoded) decoded.recycle()
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "pano-${UUID.randomUUID()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        val ok = PanoramaPrep.Ok(file.absolutePath, out.width, out.height)
        out.recycle()
        ok
    }.getOrElse { PanoramaPrep.Unreadable }
}
