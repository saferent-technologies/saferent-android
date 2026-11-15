package com.saferenttechnologies.android.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

// ---------------- listing feed ----------------

/** One card in the Explore feed (GET browse). Money is kept as text from the server → [Money]. */
data class Listing(
    val id: String,
    val code: String = "",
    val title: String = "",
    val category: String = "",
    val propertyType: String = "",
    val propertyTypeLabel: String = "",
    val listingType: String = "RENT",
    val locality: String = "",
    val city: String = "",
    val state: String = "",
    val rentFrom: String = "",
    val rentTo: String = "",
    val availableCount: Int = 0,
    val completenessScore: Int = 0,
    val verified: Boolean = false,
    val ownerVerified: Boolean = false,
    val coverImageId: String? = null,
    val distanceKm: Double? = null,
)

data class ListingPage(val items: List<Listing>, val page: Int, val totalPages: Int, val total: Int) {
    val hasMore: Boolean get() = page < totalPages
}

enum class BrowseSort(val key: String) {
    NEWEST("newest"), RENT_ASC("rent_asc"), RENT_DESC("rent_desc"), COMPLETENESS("completeness"), NEAREST("nearest");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: NEWEST
    }
}

/** A place picked from the suggestions (exact city / locality / state). */
data class PlaceFilter(val label: String, val city: String? = null, val locality: String? = null, val state: String? = null)

/** "Near me": one fix + radius. */
data class NearMe(val latitude: Double, val longitude: Double, val radiusKm: Int = 10)

/**
 * Everything the feed is filtered by. Immutable; [toQuery] builds GET browse params.
 * Free text ([query]) and an exact [place] never go together (picking a place clears the text).
 */
data class BrowseFilters(
    val query: String = "",
    val place: PlaceFilter? = null,
    val nearMe: NearMe? = null,
    val listingType: String? = null,
    val category: String? = null,
    val propertyType: String? = null,
    val minRent: Int? = null,
    val maxRent: Int? = null,
    val bedrooms: Int? = null,
    val furnishing: String? = null,
    val availableOnly: Boolean = true,
    val sort: BrowseSort = BrowseSort.NEWEST,
) {
    /** Badge on the Filter button (sort, search and place not counted). */
    val activeCount: Int
        get() = listOfNotNull(listingType, category, propertyType, minRent, maxRent, bedrooms, furnishing).size +
            (if (!availableOnly) 1 else 0)

    val isEmpty: Boolean get() = this == BrowseFilters(sort = sort) || this == BrowseFilters()

    fun toQuery(page: Int, pageSize: Int): Map<String, String> = buildMap {
        put("page", page.toString())
        put("pageSize", pageSize.toString())
        if (place != null) {
            place.city?.let { put("city", it) }
            place.locality?.let { put("locality", it) }
            place.state?.let { put("state", it) }
        } else if (query.isNotBlank()) {
            put("q", query.trim())
        }
        listingType?.let { put("listingType", it) }
        category?.let { put("category", it) }
        propertyType?.let { put("propertyType", it) }
        minRent?.let { put("minRent", it.toString()) }
        maxRent?.let { put("maxRent", it.toString()) }
        bedrooms?.let { put("bedrooms", it.toString()) }
        furnishing?.let { put("furnishing", it) }
        put("availableOnly", availableOnly.toString())
        nearMe?.let {
            put("lat", coordinate(it.latitude))
            put("lng", coordinate(it.longitude))
            put("radiusKm", it.radiusKm.toString())
        }
        // "nearest" needs a location (else the server answers 400).
        put("sort", (if (sort == BrowseSort.NEAREST && nearMe == null) BrowseSort.NEWEST else sort).key)
    }

    /** Clear all filters (keeps nothing but the default sort). */
    fun cleared() = BrowseFilters()

    companion object {
        /** 6 decimals (≈ 10 cm) — what the server expects. */
        fun coordinate(v: Double): String = BigDecimal(v).setScale(6, RoundingMode.HALF_UP).toPlainString()
        const val BUDGET_MAX = 100_000
        const val BUDGET_STEP = 1_000
    }
}

/** GET browse/locations item. */
data class LocationSuggestion(
    val kind: String,
    val name: String,
    val label: String,
    val subLabel: String,
    val listingCount: Int,
    val filter: PlaceFilter,
)

// ---------------- listing detail ----------------

data class CostLine(val label: String, val amount: String?, val frequency: String = "") {
    /** No amount → "Not disclosed" (shown in amber). */
    val disclosed: Boolean get() = !amount.isNullOrBlank()
}

data class CostBreakdown(val monthlyTotal: String = "", val moveInTotal: String = "", val lines: List<CostLine> = emptyList())

/** A flat / unit the tenant can pick. */
data class UnitOption(
    val id: String,
    val unitNumber: String,
    val floorNumber: Int? = null,
    val bedrooms: Int? = null,
    val bathrooms: Int? = null,
    val bhkType: String = "",
    val furnishing: String = "",
    val builtUpArea: String = "",
    val availabilityStatus: String = "",
    val monthlyRent: String = "",
    val securityDeposit: String = "",
    val salePrice: String = "",
    val breakdown: CostBreakdown? = null,
    val availableFrom: String = "",
    /** Server text: "Available now" / "Available from 30 Nov 2026" / "Occupied". */
    val availabilityLabel: String = "",
    /** Null when the server didn't say (older API). */
    val availableNow: Boolean? = null,
    /** Apartment: amenities inside this flat. */
    val amenities: List<Disclosure> = emptyList(),
    /** Apartment: this flat's own photos. */
    val imageIds: List<String> = emptyList(),
) {
    val available: Boolean get() = availabilityStatus.equals("AVAILABLE", true)
}

/** PG: a bed in a room. */
data class BedOption(
    val id: String,
    val roomNumber: String,
    val bedNumber: String,
    val sharingType: String = "",
    val rent: String = "",
    val availabilityStatus: String = "",
) {
    val available: Boolean get() = availabilityStatus.equals("AVAILABLE", true)
}

data class Disclosure(val key: String, val label: String, val status: String)

/** PG / hostel room on a listing: own rent per bed, deposit, amenities, photos; beds to apply for. */
data class RoomOption(
    val id: String,
    val roomNumber: String,
    val sharingType: String = "",
    val attachedBathroom: String = "",
    val acAvailable: String = "",
    val rentPerBed: String = "",
    val securityDeposit: String = "",
    val totalBeds: Int = 0,
    val availableBeds: Int = 0,
    val beds: List<BedOption> = emptyList(),
    val imageIds: List<String> = emptyList(),
    val amenities: List<Disclosure> = emptyList(),
    val breakdown: CostBreakdown? = null,
)

data class ListingPhoto(val id: String, val category: String, val url: String, val unitId: String? = null)

data class MyApplicationRef(val id: String, val status: String, val unitId: String? = null, val bedId: String? = null)

data class ListingDetail(
    val id: String,
    val code: String = "",
    val propertyType: String = "",
    val title: String = "",
    val description: String = "",
    val propertyTypeLabel: String = "",
    val structure: PropertyStructure = PropertyStructure.MULTI_UNIT,
    val listingType: String = "RENT",
    val locality: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val building: Map<String, String> = emptyMap(),
    val verified: Boolean = false,
    val ownerVerified: Boolean = false,
    val completenessScore: Int = 0,
    val completenessNote: String = "",
    val missing: List<String> = emptyList(),
    val rentFrom: String = "",
    val rentTo: String = "",
    val availableCount: Int = 0,
    val units: List<UnitOption> = emptyList(),
    val beds: List<BedOption> = emptyList(),
    val rooms: List<RoomOption> = emptyList(),
    val amenities: List<Disclosure> = emptyList(),
    val utilities: List<Disclosure> = emptyList(),
    val policies: List<Disclosure> = emptyList(),
    val photos: List<ListingPhoto> = emptyList(),
    val disclosureLegend: Map<String, String> = emptyMap(),
    val myApplication: MyApplicationRef? = null,
) {
    val usesBeds: Boolean get() = structure.usesRooms

    /** Flats / portions / shops / community villas → "Choose a {unit}" layout (photos / price / amenities per unit). */
    val isUnitPriced: Boolean get() = UnitPricing.isUnitPriced(propertyType, building["villaListing"]) && units.isNotEmpty()

    /** PG / hostel with rooms → room cards (rent per bed, deposit, amenities, photos, beds). */
    val isRoomPriced: Boolean get() = structure.usesRooms && rooms.isNotEmpty()

    /** "Flat" / "Portion" / "Shop" / "Villa" / "Room". */
    val unitWord: String get() = UnitPricing.unitWord(propertyType, null, structure)
}

/** Apartment listing: which flat is shown and its photos (pure, unit-tested). */
object FlatSelection {
    /** Kept flat if it still exists, else the first AVAILABLE one, else the first. */
    fun default(detail: ListingDetail, current: String? = null): String? =
        detail.units.firstOrNull { it.id == current }?.id
            ?: detail.units.firstOrNull { it.available }?.id
            ?: detail.units.firstOrNull()?.id

    /** The flat's own photos first, then the building's (unitId = null). Other types: every photo. */
    fun photos(detail: ListingDetail, unitId: String?): List<ListingPhoto> {
        if (!detail.isUnitPriced) return detail.photos
        val unit = detail.units.firstOrNull { it.id == unitId }
        val flat = detail.photos.filter { p -> unit != null && (p.unitId == unit.id || p.id in unit.imageIds) }
        val building = detail.photos.filter { it.unitId == null && it !in flat }
        return flat + building
    }
}

data class ReviewSummary(val average: Double?, val count: Int, val categories: Map<String, Double>)
data class Review(val id: String, val rating: Double?, val comment: String, val authorName: String, val createdAt: String)
data class ReviewsPreview(val summary: ReviewSummary, val reviews: List<Review>)

// ---------------- apply / enquire / report ----------------

data class ApplyInput(
    val unitId: String? = null,
    val bedId: String? = null,
    val proposedMoveIn: SimpleDate?,
    val occupants: Int,
    val message: String = "",
)

enum class ApplyError { CHOOSE_HOME, MOVE_IN_REQUIRED, MOVE_IN_PAST, OCCUPANTS }

object ApplyRules {
    const val MIN_OCCUPANTS = 1
    const val MAX_OCCUPANTS = 20

    fun validate(input: ApplyInput, today: SimpleDate): ApplyError? = when {
        input.unitId == null && input.bedId == null -> ApplyError.CHOOSE_HOME
        input.proposedMoveIn == null -> ApplyError.MOVE_IN_REQUIRED
        input.proposedMoveIn < today -> ApplyError.MOVE_IN_PAST
        input.occupants !in MIN_OCCUPANTS..MAX_OCCUPANTS -> ApplyError.OCCUPANTS
        else -> null
    }
}

/** Why apply failed, so the screen can react (open the review, show the server message …). */
sealed interface ApplyFailure {
    val message: String
    data class AlreadyApplied(override val message: String) : ApplyFailure
    data class ReviewRequired(override val message: String) : ApplyFailure
    data class NotAvailable(override val message: String) : ApplyFailure
    data class Other(override val message: String, val error: AppError) : ApplyFailure

    companion object {
        /** 409 → already applied; 412 REVIEW_REQUIRED → review first; other 412 → not available. */
        fun from(error: AppError): ApplyFailure = when (error) {
            is AppError.Conflict -> AlreadyApplied(error.message)
            is AppError.Server -> when {
                error.httpCode == 409 -> AlreadyApplied(error.message)
                error.httpCode == 412 && (error.errorCode == "REVIEW_REQUIRED" || error.reason == "REVIEW_REQUIRED") -> ReviewRequired(error.message)
                error.httpCode == 412 -> NotAvailable(error.message)
                else -> Other(error.message, error)
            }
            is AppError.InvalidState -> NotAvailable(error.message)
            else -> Other(error.message, error)
        }
    }
}

val REPORT_REASONS = listOf("FAKE_LISTING", "WRONG_PRICE", "SCAM_REQUEST", "DUPLICATE", "INAPPROPRIATE", "OTHER")

// ---------------- tenant ----------------

data class TenantLease(
    val id: String,
    val code: String = "",
    val propertyId: String = "",
    val propertyTitle: String = "",
    val place: String = "",
    val monthlyRent: String = "",
    val rentDueDay: Int? = null,
    val startDate: String = "",
    val endDate: String = "",
    val landlordName: String = "",
)

data class TenantApplication(
    val id: String,
    val status: String,
    val proposedMoveIn: String = "",
    val occupants: Int? = null,
    val propertyId: String = "",
    val propertyTitle: String = "",
    val city: String = "",
    val place: String = "",
    val createdAt: String = "",
)

// ---------------- formatting ----------------

object Money {
    /** "18000.00" → "₹18,000"; 100000 → "₹1,00,000" (Indian grouping). Blank / bad → "". */
    fun inr(value: String?): String {
        val n = value?.trim()?.toBigDecimalOrNull() ?: return ""
        return inr(n)
    }

    fun inr(n: BigDecimal): String {
        val whole = n.setScale(0, RoundingMode.HALF_UP).toBigInteger()
        val negative = whole.signum() < 0
        val digits = whole.abs().toString()
        val grouped = if (digits.length <= 3) digits else {
            val last3 = digits.takeLast(3)
            val rest = digits.dropLast(3)
            rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
        }
        return (if (negative) "-₹" else "₹") + grouped
    }

    /** "₹18,000 – ₹22,000" or one price when equal / only one. */
    fun range(from: String?, to: String?): String {
        val a = inr(from)
        val b = inr(to)
        return when {
            a.isEmpty() -> b
            b.isEmpty() || a == b -> a
            else -> "$a – $b"
        }
    }

    /** 1.4 → "1.4 km", 0.35 → "350 m". */
    fun distance(km: Double): String =
        if (km < 1) "${(km * 1000).toInt()} m" else "${BigDecimal(km).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} km"
}
