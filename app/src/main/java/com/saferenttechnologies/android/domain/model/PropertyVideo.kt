package com.saferenttechnologies.android.domain.model

/** Video tour of one flat / PG room (unitId / roomId) or of the whole building (both null). */
data class PropertyVideo(
    val id: String,
    val url: String = "",
    val thumbnailUrl: String = "",
    val durationSec: Int = 0,
    /** PENDING until SafeRent checks it, then APPROVED / REJECTED. */
    val status: String = "PENDING",
    val unitId: String? = null,
    val roomId: String? = null,
    /** Why SafeRent rejected it (owner only). */
    val rejectionReason: String = "",
) {
    val isBuilding: Boolean get() = unitId == null && roomId == null
    val isRejected: Boolean get() = status.equals("REJECTED", true)
    val isLive: Boolean get() = status.equals("APPROVED", true)
}

/**
 * In-app recording only: 1080p (720p fallback) H.264 at a fixed bitrate → sharp but small
 * (~26 MB per minute), no sound, SafeRent watermark drawn into the middle of every frame.
 */
object VideoRules {
    const val MAX_SECONDS = 60
    const val MIN_SECONDS = 5
    /** ≈ 3.5 Mbps: clear 1080p for a walkthrough, small upload. */
    const val BITRATE = 3_500_000
    /** Request videos: 720p ≈ 2.5 Mbps (60 s ≈ 19 MB, under the 40 MB limit). */
    const val BITRATE_720 = 2_500_000
    /** One video per flat / room / building (a new one replaces the old one). */
    const val PER_TARGET = 1

    fun canStop(seconds: Int): Boolean = seconds >= MIN_SECONDS
    fun isTooLong(seconds: Int): Boolean = seconds >= MAX_SECONDS

    /** "0:07" / "1:00". */
    fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    /**
     * Flat chosen → ONLY that flat's video (no building video). Flat has none, or no flat chosen →
     * the building's video; no building video either (no flat chosen) → the flats' videos. Other types: all.
     */
    fun forFlat(videos: List<PropertyVideo>, unitId: String?, unitPriced: Boolean): List<PropertyVideo> {
        if (!unitPriced) return videos
        val flat = videos.filter { unitId != null && it.unitId == unitId }
        if (flat.isNotEmpty()) return flat
        val building = videos.filter { it.unitId == null && it.roomId == null }
        if (unitId == null && building.isEmpty()) return videos.filter { it.unitId != null }
        return building
    }

    /** Flats ("302") that have no video yet: none on the server and none uploading. Pure, unit-tested. */
    fun unitsWithoutVideo(
        units: List<Pair<String, String>>,
        serverVideos: List<PropertyVideo>,
        uploadingUnitIds: Set<String>,
    ): List<String> {
        val have = serverVideos.mapNotNull { it.unitId }.toSet() + uploadingUnitIds
        return units.filter { (id, _) -> id !in have }.map { (id, number) -> number.ifBlank { id } }
    }
}
