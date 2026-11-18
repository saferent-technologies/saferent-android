package com.saferenttechnologies.android.domain.model

/**
 * Tenant "Open to new homes": when on, the server sends a push when a home that matches goes live
 * within [radiusKm] of the chosen spot (budget / types optional).
 */
data class HomeAlert(
    val enabled: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** "Madhapur, Hyderabad" (device Geocoder). */
    val areaLabel: String = "",
    /** Picked from our own locations (search): matched by name on the server too. */
    val locality: String = "",
    val city: String = "",
    val radiusKm: Int = HomeAlertRules.DEFAULT_RADIUS,
    /** Max monthly rent in ₹; null = any. */
    val maxRent: Int? = null,
    /** Property type keys; empty = every type. */
    val propertyTypes: Set<String> = emptySet(),
) {
    val hasArea: Boolean get() = latitude != null && longitude != null
    val hasPlace: Boolean get() = locality.isNotBlank() || city.isNotBlank()
    /** A spot (GPS / area centre) or a place from our list. */
    val hasWhere: Boolean get() = hasArea || hasPlace
}

object HomeAlertRules {
    const val DEFAULT_RADIUS = 5
    val RADII = listOf(2, 5, 10, 20)
    /** null = any budget. */
    val BUDGETS: List<Int?> = listOf(null, 10_000, 15_000, 20_000, 30_000, 50_000)

    /** On needs a place; off can always be saved. */
    fun canSave(a: HomeAlert): Boolean = !a.enabled || a.hasWhere

    /** Search shows only areas and cities from our own listings. */
    fun searchable(kind: String): Boolean = kind.uppercase() in setOf("LOCALITY", "CITY")

    /** "Madhapur, Hyderabad" from the Geocoder result (locality + city, else the full line). */
    fun areaLabel(address: GeoAddress?): String {
        if (address == null) return ""
        val parts = listOf(address.locality, address.city).filter { it.isNotBlank() }.distinct()
        return parts.joinToString(", ").ifBlank { address.formattedAddress }
    }
}
