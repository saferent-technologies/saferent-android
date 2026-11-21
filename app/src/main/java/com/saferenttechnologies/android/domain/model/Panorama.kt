package com.saferenttechnologies.android.domain.model

/**
 * 360° photos of a flat / room (or, optionally, the whole building). Landlords take them with
 * SafeRent's own guided 360 camera ([PanoramaCapture]); tenants look around in an OpenGL viewer
 * (drag, pinch, or move the phone).
 */
/** App features built but switched off for now (turn on to ship). Not the server's config Features. */
object AppFeatureFlags {
    /** 360° photos: capture in the wizard + viewer in listings. Off until we launch it. */
    const val PANORAMA_360 = false
}

object PanoramaRules {
    /** Image category used for these photos (kept out of the normal 4:3 galleries). */
    const val CATEGORY = "PANORAMA_360"

    /** A panorama is clearly wider than tall (≥ 1.9 : 1). */
    const val MIN_RATIO = 1.9f

    /** Full 360 × 180 photo sphere ("equirectangular") ≈ 2 : 1. */
    private const val SPHERE_MIN = 1.9f
    private const val SPHERE_MAX = 2.1f

    /** Phone panoramas cover about this much up / down. */
    const val PANO_VERTICAL_DEG = 65f

    /** Longest side we keep / upload (fits GPU textures on most phones). */
    const val MAX_WIDTH = 4096
    const val MAX_HEIGHT = 2048

    fun isPanorama(width: Int, height: Int): Boolean = width > 0 && height > 0 && width.toFloat() / height >= MIN_RATIO

    /**
     * How to wrap the photo: a 2:1 photo sphere goes on a full sphere; a phone panorama goes on a
     * cylinder covering width/height × 65° (capped at 360°), 65° tall.
     */
    fun projection(width: Int, height: Int): PanoramaProjection {
        val ratio = if (height > 0) width.toFloat() / height else 2f
        return if (ratio in SPHERE_MIN..SPHERE_MAX) {
            PanoramaProjection(sphere = true, horizontalDeg = 360f, verticalDeg = 180f)
        } else {
            PanoramaProjection(sphere = false, horizontalDeg = (ratio * PANO_VERTICAL_DEG).coerceAtMost(360f), verticalDeg = PANO_VERTICAL_DEG)
        }
    }

    /** Size to scale a picked photo to (keeps the ratio; never bigger than the original). */
    fun scaledSize(width: Int, height: Int): Pair<Int, Int> {
        val scale = minOf(1f, MAX_WIDTH.toFloat() / width, MAX_HEIGHT.toFloat() / height)
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    fun isPanoramaCategory(category: String?): Boolean = category.equals(CATEGORY, ignoreCase = true)
}

data class PanoramaProjection(val sphere: Boolean, val horizontalDeg: Float, val verticalDeg: Float)

/**
 * Our own 360 capture (no camera app has a public "open in Panorama mode" intent): the landlord
 * stands in the middle of the room, holds the phone upright and turns right; a photo is taken
 * automatically every [STEP_DEG]. Pure maths here (unit-tested); camera + stitching in presentation.
 */
object PanoramaCapture {
    /** One photo every 30° → 12 photos for a full turn (neighbours overlap by ~18°). */
    const val STEP_DEG = 30f
    const val FULL_TURN_DEG = 360f

    /** "Done" only after at least this much turning (makes the result clearly wider than tall). */
    const val MIN_SPAN_DEG = 120f

    /** Shoot when the phone is this close to the next target. */
    const val ALIGN_TOL_DEG = 4f

    /** Phone must be upright and level within this (tilting up/down or sideways breaks the stitch). */
    const val LEVEL_TOL_DEG = 10f

    /** Portrait field of view used when the camera doesn't report one. */
    const val DEFAULT_HFOV_DEG = 48f

    /** Each photo is scaled to this (portrait) before stitching (keeps memory small). */
    const val FRAME_WIDTH = 720
    const val FRAME_HEIGHT = 960

    /** Angle in (-180, 180]. */
    fun normalize(deg: Float): Float {
        var d = deg % 360f
        if (d <= -180f) d += 360f
        if (d > 180f) d -= 360f
        return d
    }

    /** Turned so far (right = positive), adding the small change since the last reading (no jump at ±180). */
    fun advance(progress: Float, lastYaw: Float, yaw: Float): Float = progress + normalize(yaw - lastYaw)

    /** Next angle to shoot at after [shots] photos. */
    fun target(shots: Int): Float = shots * STEP_DEG

    fun isAligned(progress: Float, shots: Int): Boolean = kotlin.math.abs(progress - target(shots)) <= ALIGN_TOL_DEG

    fun isLevel(pitchDeg: Float, rollDeg: Float): Boolean =
        kotlin.math.abs(pitchDeg) <= LEVEL_TOL_DEG && kotlin.math.abs(rollDeg) <= LEVEL_TOL_DEG

    /** All 12 taken → a full circle. */
    fun isFullTurn(shots: Int): Boolean = shots * STEP_DEG >= FULL_TURN_DEG

    /** Angle the photos cover so far (first to last + one field of view). */
    fun span(shots: Int, hfovDeg: Float): Float = if (shots <= 0) 0f else ((shots - 1) * STEP_DEG + hfovDeg).coerceAtMost(FULL_TURN_DEG)

    fun canFinish(shots: Int, hfovDeg: Float): Boolean = shots >= 2 && span(shots, hfovDeg) >= MIN_SPAN_DEG
}
