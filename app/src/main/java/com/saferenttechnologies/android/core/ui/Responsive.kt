package com.saferenttechnologies.android.core.ui

import android.content.res.Configuration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Own sdp / ssp (no library): sizes are written for a [BASE_WIDTH_DP]-wide phone and scale
 * with the device's smallest width, so every screen keeps the same proportions on small
 * phones, big phones and tablets.
 *
 *   16.sdp → 16dp on a 360dp phone, ~18dp on a 411dp phone, 14.4dp on a 320dp phone
 *   14.ssp → same idea for text (the user's font-size setting still applies on top, as sp)
 *
 * The factor is clamped so tablets don't get giant UI and tiny phones stay readable.
 * Set once from the Application / Activity (see [update]); previews use 1×.
 */
object ResponsiveScale {
    /** Width the current designs were built for. */
    const val BASE_WIDTH_DP = 360f
    private const val MIN_FACTOR = 0.85f
    private const val MAX_FACTOR = 1.30f

    @Volatile
    var factor: Float = 1f
        private set

    fun update(configuration: Configuration) = update(configuration.smallestScreenWidthDp)

    fun update(smallestWidthDp: Int) {
        if (smallestWidthDp <= 0) return // undefined → keep 1×
        factor = (smallestWidthDp / BASE_WIDTH_DP).coerceIn(MIN_FACTOR, MAX_FACTOR)
    }
}

/** Scalable dp (like the sdp library). */
val Int.sdp: Dp get() = (this * ResponsiveScale.factor).dp
val Float.sdp: Dp get() = (this * ResponsiveScale.factor).dp
val Double.sdp: Dp get() = (this.toFloat() * ResponsiveScale.factor).dp

/** Scalable sp (like the ssp library). */
val Int.ssp: TextUnit get() = (this * ResponsiveScale.factor).sp
val Float.ssp: TextUnit get() = (this * ResponsiveScale.factor).sp
val Double.ssp: TextUnit get() = (this.toFloat() * ResponsiveScale.factor).sp
