package com.saferenttechnologies.android.presentation.panorama

import android.graphics.Bitmap
import com.saferenttechnologies.android.domain.model.PanoramaRules
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

/** One guided-capture photo: portrait bitmap + how far the phone had turned (degrees, right = +). */
class PanoramaFrame(val bitmap: Bitmap, val yawDeg: Float)

/**
 * Joins the guided photos into one wide picture (no libraries): each photo is bent onto a cylinder
 * around the landlord using the phone's turn angle, neighbours are blended where they overlap, and
 * brightness is evened out. Result fits [PanoramaRules.MAX_WIDTH] × [PanoramaRules.MAX_HEIGHT].
 * Heavy: call on a background thread.
 */
object PanoramaStitcher {
    fun stitch(frames: List<PanoramaFrame>, hfovDeg: Float): Bitmap {
        require(frames.isNotEmpty())
        val w = frames[0].bitmap.width
        val h = frames[0].bitmap.height
        val halfFov = hfovDeg / 2f
        val f = (w / 2.0) / tan(Math.toRadians(halfFov.toDouble()))

        val startDeg = frames.first().yawDeg - halfFov
        val rawSpan = frames.last().yawDeg + halfFov - startDeg
        val full = rawSpan >= 360f
        val spanDeg = if (full) 360f else rawSpan
        val outW = (f * Math.toRadians(spanDeg.toDouble())).toInt().coerceAtLeast(1)
        val outH = h

        val pixels = frames.map { fr -> IntArray(w * h).also { fr.bitmap.getPixels(it, 0, w, 0, 0, w, h) } }
        val gains = gains(pixels, w, h)
        val out = IntArray(outW * outH)

        val idx = IntArray(frames.size)
        val xs = IntArray(frames.size)
        val invCos = DoubleArray(frames.size)
        val weight = FloatArray(frames.size)
        for (u in 0 until outW) {
            val angle = startDeg + Math.toDegrees(u / f).toFloat()
            var n = 0
            for (i in frames.indices) {
                var d = angle - frames[i].yawDeg
                if (full) d = wrap(d)
                if (abs(d) >= halfFov) continue
                val phi = Math.toRadians(d.toDouble())
                val x = (f * tan(phi) + w / 2.0).toInt()
                if (x < 0 || x >= w) continue
                idx[n] = i; xs[n] = x; invCos[n] = 1.0 / cos(phi); weight[n] = halfFov - abs(d); n++
            }
            if (n == 0) continue
            for (v in 0 until outH) {
                val dy = v - outH / 2.0
                var r = 0f; var g = 0f; var b = 0f; var wsum = 0f
                for (k in 0 until n) {
                    val y = (dy * invCos[k] + h / 2.0).toInt()
                    if (y < 0 || y >= h) continue
                    val p = pixels[idx[k]][y * w + xs[k]]
                    val wt = weight[k]
                    val gn = gains[idx[k]] * wt
                    r += ((p shr 16) and 0xFF) * gn; g += ((p shr 8) and 0xFF) * gn; b += (p and 0xFF) * gn
                    wsum += wt
                }
                if (wsum > 0f) {
                    out[v * outW + u] = (0xFF shl 24) or (clamp(r / wsum) shl 16) or (clamp(g / wsum) shl 8) or clamp(b / wsum)
                }
            }
        }
        val stitched = Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888)
        val (sw, sh) = PanoramaRules.scaledSize(outW, outH)
        return if (sw == outW && sh == outH) stitched else Bitmap.createScaledBitmap(stitched, sw, sh, true).also { stitched.recycle() }
    }

    /** Evens out exposure: each photo's brightness pulled towards the average (0.8×..1.25×). */
    private fun gains(pixels: List<IntArray>, w: Int, h: Int): FloatArray {
        val means = pixels.map { px ->
            var sum = 0L; var count = 0
            var y = h / 4
            while (y < h * 3 / 4) {
                var x = w / 4
                while (x < w * 3 / 4) {
                    val p = px[y * w + x]
                    sum += (((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 6 + (p and 0xFF)) / 10
                    count++; x += 8
                }
                y += 8
            }
            if (count == 0) 128f else sum.toFloat() / count
        }
        val avg = means.average().toFloat()
        return FloatArray(pixels.size) { i -> if (means[i] <= 1f) 1f else (avg / means[i]).coerceIn(0.8f, 1.25f) }
    }

    private fun wrap(d: Float): Float {
        var x = d % 360f
        if (x > 180f) x -= 360f
        if (x < -180f) x += 360f
        return x
    }

    private fun clamp(v: Float): Int = v.toInt().coerceIn(0, 255)
}
