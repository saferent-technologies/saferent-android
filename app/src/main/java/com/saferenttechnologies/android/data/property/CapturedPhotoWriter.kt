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

    /** Property photos are 4:3 (landscape), centre-cropped. Documents keep the full frame. */
    const val PHOTO_ASPECT = 4f / 3f

    /** Faint centre watermark on every property photo. */
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

    /** Watermark opacity (0–255): faint, like a watermark, so the photo stays clear. */
    private const val MARK_ALPHA = 70

    /**
     * Centre of the photo: SafeRent logo + "SafeRent" side by side, faint and see-through (like a
     * watermark), sized to the photo, with a very light shadow so it shows on bright walls too.
     */
    fun drawWatermark(context: Context, bitmap: Bitmap, text: String): Bitmap {
        val out = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = MARK_ALPHA
            textSize = minOf(out.width, out.height) * 0.09f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, Color.argb(45, 0, 0, 0))
        }
        val logo = ContextCompat.getDrawable(context, R.drawable.app_logo)?.mutate()
        val logoSize = (paint.textSize * 1.4f).toInt()
        val gap = paint.textSize * 0.25f
        val textWidth = paint.measureText(text)
        val groupWidth = textWidth + if (logo != null) logoSize + gap else 0f
        val left = (out.width - groupWidth) / 2f
        val centerY = out.height / 2f
        // Baseline so the letters sit centred on the photo's middle line.
        val baseline = centerY - (paint.descent() + paint.ascent()) / 2f
        logo?.let {
            val top = (centerY - logoSize / 2f).toInt()
            it.setBounds(left.toInt(), top, left.toInt() + logoSize, top + logoSize)
            it.alpha = MARK_ALPHA
            it.draw(canvas)
        }
        canvas.drawText(text, left + if (logo != null) logoSize + gap else 0f, baseline, paint)
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
