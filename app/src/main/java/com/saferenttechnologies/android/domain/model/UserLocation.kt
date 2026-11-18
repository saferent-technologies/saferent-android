package com.saferenttechnologies.android.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The user's area (not their exact spot): ~1 km rounded point + area names from the phone. */
data class UserArea(
    val latitude: Double,
    val longitude: Double,
    val locality: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
)

/** Last area sent to the server (kept on the phone to avoid sending the same thing again). */
data class SentArea(val latitude: Double, val longitude: Double, val atMillis: Long, val locality: String = "", val city: String = "")

object UserLocationRules {
    /** Same area name but moved more than this → send the new point. */
    const val RESEND_AFTER_KM = 2.0
    /** GPS is checked at most this often (not on every app open). */
    const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    fun shouldCheck(lastCheckMillis: Long?, nowMillis: Long): Boolean = lastCheckMillis == null || nowMillis - lastCheckMillis >= CHECK_EVERY_MS

    /** 2 decimals ≈ 1 km: enough for "homes near you" and search, never the exact house. */
    fun coarse(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0

    /** Same (rounded) point as last time → nothing to send, ever (no duplicate rows). */
    fun samePoint(last: SentArea?, latitude: Double, longitude: Double): Boolean =
        last != null && coarse(last.latitude) == latitude && coarse(last.longitude) == longitude

    private fun norm(s: String) = s.trim().lowercase()

    /**
     * Send only when something really changed: a new point AND (a different area name or a move
     * of more than [RESEND_AFTER_KM]). Same name + same point is never sent again.
     */
    fun shouldSend(last: SentArea?, latitude: Double, longitude: Double, locality: String, city: String): Boolean {
        if (last == null) return true
        if (samePoint(last, latitude, longitude)) return false
        val sameName = norm(last.locality) == norm(locality) && norm(last.city) == norm(city) && (locality.isNotBlank() || city.isNotBlank())
        return !sameName || distanceKm(last.latitude, last.longitude, latitude, longitude) > RESEND_AFTER_KM
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
