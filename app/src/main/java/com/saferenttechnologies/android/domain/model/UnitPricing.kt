package com.saferenttechnologies.android.domain.model

/**
 * Buildings with separately rented units (pure, unit-tested):
 * - unit-priced: APARTMENT (flats), INDEPENDENT_BUILDING (portions), COMMERCIAL_COMPLEX (shops),
 *   VILLA with building.villaListing == COMMUNITY (villas) → rent / deposit / amenities / photos on each unit;
 * - room-priced: PG / hostel (ROOMS_BEDS) → rent per bed, deposit, amenities, photos on each room.
 * The building itself only has common charges, amenities and photos. Every other type is unchanged.
 */
object UnitPricing {
    val UNIT_PRICED_TYPES = setOf("APARTMENT", "INDEPENDENT_BUILDING", "COMMERCIAL_COMPLEX")

    fun isUnitPriced(propertyType: String?, villaListing: String?): Boolean {
        val type = propertyType?.trim()?.uppercase() ?: return false
        return type in UNIT_PRICED_TYPES || (type == "VILLA" && villaListing.equals("COMMUNITY", ignoreCase = true))
    }

    fun isRoomPriced(structure: PropertyStructure): Boolean = structure.usesRooms

    /** Rent / deposit / sale price live on the units or rooms, never on the building. */
    fun hidesBuildingPrice(propertyType: String?, villaListing: String?, structure: PropertyStructure): Boolean =
        isUnitPriced(propertyType, villaListing) || isRoomPriced(structure)

    /** "Flat" / "Portion" / "Shop" / "Villa" / "Room" — the server's unitLabel wins when it sends one. */
    fun unitWord(propertyType: String?, unitLabel: String? = null, structure: PropertyStructure? = null): String {
        unitLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        if (structure?.usesRooms == true) return "Room"
        return when (propertyType?.trim()?.uppercase()) {
            "APARTMENT" -> "Flat"
            "INDEPENDENT_BUILDING" -> "Portion"
            "COMMERCIAL_COMPLEX" -> "Shop"
            "VILLA" -> "Villa"
            "PG", "HOSTEL", "PG_HOSTEL" -> "Room"
            else -> "Unit"
        }
    }

    /** PG / hostel listing keys (the feed card has no structure). */
    fun isPgType(propertyType: String?): Boolean =
        propertyType?.uppercase()?.let { it == "PG" || it == "HOSTEL" || it == "PG_HOSTEL" || it.startsWith("PG") } == true
}
