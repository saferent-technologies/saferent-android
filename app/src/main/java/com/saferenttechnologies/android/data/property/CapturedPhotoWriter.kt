package com.saferenttechnologies.android.data.property

import com.saferenttechnologies.android.R
import androidx.core.content.ContextCompat
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Canvas
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Captured frame → upload-ready JPEG in the cache dir: upright, longest side ≤ 1920 px,
 * quality ~85 (lowered until ≤ 2 MB). Re-encoding writes NO EXIF, so GPS etc. never leave the phone.
 */
object CapturedPhotoWriter {
    const val MAX_SIDE = 1920
    const val START_QUALITY = 85
    const val MAX_BYTES = 2 * 1024 * 1024

    /** Property photos are 16:9 (landscape), centre-cropped. Documents keep the full frame. */
    const val PHOTO_ASPECT = 16f / 9f

    /** Bottom-right mark on every property photo. */
    const val WATERMARK = "SafeRent"

    fun write(context: Context, source: Bitmap, rotationDegrees: Int, aspect: Float? = null, watermark: String? = null): File {
        val rotated = if (rotationDegrees % 360 != 0) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
        } else {
            source
        }
        val upright = aspect?.let { centerCrop(rotated, it) } ?: rotated
        if (upright !== rotated && rotated !== source) rotated.recycle()
        val longest = max(upright.width, upright.height)
        val scaled = if (longest > MAX_SIDE) {
            val factor = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(upright, (upright.width * factor).roundToInt(), (upright.height * factor).roundToInt(), true)
        } else {
            upright
        }
        val marked = watermark?.let { drawWatermark(context, scaled, it) } ?: scaled
        if (marked !== scaled && scaled !== upright && scaled !== source) scaled.recycle()
        var quality = START_QUALITY
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                marked.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 40)

        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.writeBytes(bytes)
        if (marked !== source && marked !== upright) marked.recycle()
        if (scaled !== source && scaled !== upright && scaled !== marked && !scaled.isRecycled) scaled.recycle()
        if (upright !== source && upright !== scaled && upright !== marked) upright.recycle()
        return file
    }

    /** Watermark opacity (0–255): see-through so the photo stays clear. */
    private const val MARK_ALPHA = 165

    /**
     * Bottom-right: SafeRent logo + "SafeRent", see-through, sized to the photo, with a soft
     * shadow on the text so it reads on light and dark backgrounds.
     */
    fun drawWatermark(context: Context, bitmap: Bitmap, text: String): Bitmap {
        val out = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = MARK_ALPHA
            textSize = out.width * 0.035f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            setShadowLayer(textSize * 0.12f, 0f, textSize * 0.06f, Color.argb(120, 0, 0, 0))
        }
        val margin = out.width * 0.025f
        val textRight = out.width - margin
        val baseline = out.height - margin
        canvas.drawText(text, textRight, baseline, paint)

        // Logo just left of the text, a bit taller than the letters (vector drawn crisp at any size).
        ContextCompat.getDrawable(context, R.drawable.app_logo)?.mutate()?.let { logo ->
            val size = (paint.textSize * 1.6f).toInt()
            val gap = paint.textSize * 0.2f
            val right = (textRight - paint.measureText(text) - gap).toInt()
            val centerY = baseline - paint.textSize * 0.35f
            val top = (centerY - size / 2f).toInt()
            logo.setBounds(right - size, top, right, top + size)
            logo.alpha = MARK_ALPHA
            logo.draw(canvas)
        }
        return out
    }

    /** Middle part of [bitmap] with width / height = [aspect] (same bitmap when it already fits). */
    fun centerCrop(bitmap: Bitmap, aspect: Float): Bitmap {
        val (w, h) = cropSize(bitmap.width, bitmap.height, aspect)
        if (w == bitmap.width && h == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, (bitmap.width - w) / 2, (bitmap.height - h) / 2, w, h)
    }

    /** Pure maths (unit-tested): largest w × h inside [width] × [height] with w / h = [aspect]. */
    fun cropSize(width: Int, height: Int, aspect: Float): Pair<Int, Int> =
        if (width.toFloat() / height > aspect) {
            (height * aspect).roundToInt().coerceAtMost(width) to height
        } else {
            width to (width / aspect).roundToInt().coerceAtMost(height)
        }
}
