package com.saferenttechnologies.android.domain.model

/** Add-property wizard steps (sent as `wizardStep` so web + app resume at the same place). */
object WizardStep {
    const val CATEGORY = 1
    const val BASIC = 2
    const val LOCATION = 3
    const val BUILDING = 4
    const val UNITS = 5
    const val PRICING = 6
    const val OPTIONAL = 7
    const val PHOTOS = 8
    const val DOCUMENTS = 9
    /** Old drafts may still be saved at 10; the wizard now ends (and submits) on DOCUMENTS. */
    const val REVIEW = 10
    const val LAST = DOCUMENTS
    const val COUNT = 9
}

/** A problem with one field. Server messages win; local ones are shown via strings.xml. */
sealed interface FieldError {
    data object Required : FieldError
    data class TooLong(val max: Int) : FieldError
    data class BelowMin(val min: Double) : FieldError
    data class AboveMax(val max: Double) : FieldError
    data object NotANumber : FieldError
    data class Server(val message: String) : FieldError
}

/** Validation for the dynamic forms (building, units, rooms, pricing…). Values are strings. */
object FieldValidator {

    fun isRequired(field: FieldDef, values: Map<String, String>): Boolean {
        val rule = field.requiredWhen ?: return field.required
        return values[rule.field].orEmpty().equals(rule.equals, ignoreCase = true)
    }

    fun validate(fields: List<FieldDef>, values: Map<String, String>): Map<String, FieldError> = buildMap {
        for (field in fields) {
            val value = values[field.key].orEmpty().trim()
            if (value.isEmpty()) {
                if (isRequired(field, values)) put(field.key, FieldError.Required)
                continue
            }
            field.maxLength?.let { if (value.length > it) put(field.key, FieldError.TooLong(it)) }
            if (field.digitsOnly && !value.all(Char::isDigit)) put(field.key, FieldError.NotANumber)
            if (field.type.numeric) {
                val number = value.toDoubleOrNull()
                when {
                    number == null -> put(field.key, FieldError.NotANumber)
                    field.type == FieldType.INTEGER && number % 1.0 != 0.0 -> put(field.key, FieldError.NotANumber)
                    field.min != null && number < field.min -> put(field.key, FieldError.BelowMin(field.min))
                    field.max != null && number > field.max -> put(field.key, FieldError.AboveMax(field.max))
                }
            }
        }
    }
}

enum class ChargeError { AMOUNT_REQUIRED, AMOUNT_NOT_ALLOWED, NOT_A_NUMBER }

/** FIXED_MONTHLY / ONE_TIME need an amount; INCLUDED_IN_RENT / NOT_APPLICABLE / NOT_DISCLOSED must not have one. */
object ChargeRules {
    private val NEEDS_AMOUNT = setOf("FIXED_MONTHLY", "ONE_TIME")
    private val NO_AMOUNT = setOf("INCLUDED_IN_RENT", "NOT_APPLICABLE", "NOT_DISCLOSED")

    fun needsAmount(billing: String): Boolean = billing in NEEDS_AMOUNT
    fun amountAllowed(billing: String): Boolean = billing !in NO_AMOUNT

    /** Index of the charge → problem. */
    fun validate(charges: List<Charge>): Map<Int, ChargeError> = buildMap {
        charges.forEachIndexed { i, c ->
            val amount = c.amount.trim()
            when {
                needsAmount(c.billing) && amount.isEmpty() -> put(i, ChargeError.AMOUNT_REQUIRED)
                !amountAllowed(c.billing) && amount.isNotEmpty() -> put(i, ChargeError.AMOUNT_NOT_ALLOWED)
                amount.isNotEmpty() && amount.toDoubleOrNull() == null -> put(i, ChargeError.NOT_A_NUMBER)
            }
        }
    }
}

/** Submit 412 → "problems" checklist; tapping one opens the matching step. */
object SubmitProblems {
    fun stepFor(problem: String): Int {
        val p = problem.lowercase()
        return when {
            listOf("pin", "address", "locality", "city", "state", "location", "latitude", "longitude").any { it in p } -> WizardStep.LOCATION
            "photo" in p || "image" in p -> WizardStep.PHOTOS
            "document" in p || "ownership" in p || "deed" in p -> WizardStep.DOCUMENTS
            listOf("rent", "deposit", "price", "charge", "maintenance").any { it in p } -> WizardStep.PRICING
            listOf("flat", "unit", "room", "bed", "villa", "house").any { it in p } -> WizardStep.UNITS
            listOf("title", "description", "listing").any { it in p } -> WizardStep.BASIC
            listOf("building", "floor", "apartment name").any { it in p } -> WizardStep.BUILDING
            listOf("utilit", "rule", "amenit", "polic").any { it in p } -> WizardStep.OPTIONAL
            listOf("type", "category").any { it in p } -> WizardStep.CATEGORY
            else -> WizardStep.LAST
        }
    }
}

/** "FOUR_SHARING" → "Four sharing" (server enum keys shown as labels). */
fun humanize(value: String): String =
    value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * App-side tweaks to server fields:
 * - flat / unit number → digits only (number keyboard)
 * - parking → Bike / Car / Both / None instead of yes-no
 */
object FieldOverrides {
    val PARKING_OPTIONS = listOf("BIKE", "CAR", "BOTH", "NONE")
    private val DIGIT_KEYS = setOf("unitNumber", "flatNumber")
    private val PARKING_KEY = Regex("parking", RegexOption.IGNORE_CASE)
    private val PARKING_COUNT = Regex("slot|count|number|spaces|total", RegexOption.IGNORE_CASE)

    /** Building step: parking isn't asked here (it is asked per unit). */
    fun hiddenInBuilding(field: FieldDef): Boolean = PARKING_KEY.containsMatchIn(field.key)

    fun adjust(field: FieldDef): FieldDef = when {
        field.key in DIGIT_KEYS -> field.copy(type = FieldType.TEXT, digitsOnly = true)
        PARKING_KEY.containsMatchIn(field.key) && !PARKING_COUNT.containsMatchIn(field.key) &&
            (field.type == FieldType.DISCLOSURE || field.type == FieldType.BOOLEAN || field.type == FieldType.SELECT || field.type == FieldType.TEXT) ->
            field.copy(type = FieldType.SELECT, options = PARKING_OPTIONS)
        else -> field
    }
}

/**
 * Apartment building limits:
 * - total floors must be at least 1;
 * - "total flats" in Building details caps how many flats can be added on the Units step.
 */
object BuildingLimits {
    private val TOTAL_FLOORS = Regex("^(total|no_?of|numberOf)_?floors?$|^floors?_?(count|total)$", RegexOption.IGNORE_CASE)
    private val TOTAL_UNITS = Regex(
        "^(total|no_?of|numberOf)_?(flats?|units?|apartments?)$|^(flats?|units?|apartments?)_?(count|total)$",
        RegexOption.IGNORE_CASE,
    )

    fun isTotalFloors(key: String) = TOTAL_FLOORS.matches(key)
    fun isTotalUnits(key: String) = TOTAL_UNITS.matches(key)

    /** Apartments: total floors / total flats become required numbers ≥ 1. */
    fun forFlats(field: FieldDef): FieldDef =
        if (isTotalFloors(field.key) || isTotalUnits(field.key)) {
            field.copy(
                type = if (field.type.numeric) field.type else FieldType.INTEGER,
                required = true,
                min = maxOf(field.min ?: 1.0, 1.0),
            )
        } else field

    /** Total flats from the building form; null = not given (no cap). */
    fun maxUnits(building: Map<String, String>): Int? =
        building.entries.firstOrNull { isTotalUnits(it.key) }?.value?.trim()?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }

    /** Units step: may another flat be added? */
    fun canAddUnit(building: Map<String, String>, currentUnits: Int): Boolean =
        maxUnits(building)?.let { currentUnits < it } ?: true

    /** Building step: total flats can't go below the flats already added. */
    fun unitsError(building: Map<String, String>, currentUnits: Int): Pair<String, FieldError>? {
        val entry = building.entries.firstOrNull { isTotalUnits(it.key) } ?: return null
        val max = entry.value.trim().toDoubleOrNull()?.toInt() ?: return null
        return if (currentUnits > max) entry.key to FieldError.BelowMin(currentUnits.toDouble()) else null
    }
}

/** One photo that must be uploaded, e.g. Bedroom 2 of flat 302. */
data class RequiredPhoto(
    val unitId: String?,
    val unitNumber: String,
    val slot: Int,
    val category: String = PhotoRequirements.BEDROOM,
    /** PG / hostel room photo (uploaded with roomId). */
    val roomId: String? = null,
)

/**
 * Mandatory photos from each unit's counts (0 → none):
 * bedrooms → Bedroom 1..n (falls back to "2BHK"), bathrooms → Bathroom 1..n, balconies → Balcony 1..n.
 * One unit (house / villa / single flat) → any photo of that category + slot counts;
 * several flats → the photo must be tagged with that flat.
 */
object PhotoRequirements {
    const val BEDROOM = "BEDROOM"
    const val BATHROOM = "BATHROOM"
    const val BALCONY = "BALCONY"
    const val FRONT_VIEW = "FRONT_VIEW"
    const val LIVING_ROOM = "LIVING_ROOM"
    const val KITCHEN = "KITCHEN"

    /** Categories counted per unit, in the order they are listed. */
    val COUNTED = listOf(LIVING_ROOM, BEDROOM, KITCHEN, BATHROOM, BALCONY)

    private val COUNT_KEYS = mapOf(
        BEDROOM to listOf("bedrooms", "bedroomCount", "noOfBedrooms"),
        BATHROOM to listOf("bathrooms", "bathroomCount", "noOfBathrooms"),
        BALCONY to listOf("balconies", "balconyCount", "noOfBalconies"),
        KITCHEN to listOf("kitchens", "kitchenCount", "noOfKitchens"),
        LIVING_ROOM to listOf("livingRooms", "livingRoomCount", "halls", "hallCount", "noOfHalls", "noOfLivingRooms"),
    )

    fun count(unit: PropertyUnit, category: String): Int {
        val fromField = COUNT_KEYS[category].orEmpty().firstNotNullOfOrNull { k -> unit.values[k]?.trim()?.toDoubleOrNull()?.toInt() }
        // No count field → read "2BHK" (2 bedrooms + hall + kitchen) / "1RK" (room + kitchen).
        val bhk = unit.values["bhkType"].orEmpty()
        val fromBhk = when (category) {
            BEDROOM -> Regex("""(\d+)\s*BHK""", RegexOption.IGNORE_CASE).find(bhk)?.groupValues?.get(1)?.toIntOrNull()
            LIVING_ROOM -> if (bhk.contains("BHK", ignoreCase = true)) 1 else null
            KITCHEN -> if (bhk.contains("BHK", ignoreCase = true) || bhk.contains("RK", ignoreCase = true)) 1 else null
            else -> null
        }
        return (fromField ?: fromBhk ?: 0).coerceIn(0, MAX_PER_CATEGORY)
    }

    fun bedrooms(unit: PropertyUnit): Int = count(unit, BEDROOM)

    /**
     * [frontView] (apartments) → the building's Front view first, then every flat's photos.
     * A flat with no bedroom / bathroom / balcony counts still needs one inside (living room) photo.
     */
    /**
     * [insideCategory] = the one photo a unit with no room counts needs (shops: OTHER).
     * PG / hostel: one photo of every room (with its roomId).
     */
    fun required(property: Property, frontView: Boolean = false, insideCategory: String = LIVING_ROOM): List<RequiredPhoto> {
        val front = if (frontView) listOf(RequiredPhoto(null, "", 1, FRONT_VIEW)) else emptyList()
        if (property.structure.usesRooms) {
            return front + property.rooms.map { r -> RequiredPhoto(null, r.roomNumber, 1, BEDROOM, roomId = r.id) }
        }
        // Always tagged with the flat, so adding a 2nd flat later doesn't re-ask the 1st one.
        return front + property.units.flatMap { unit ->
            val unitId = unit.id
            val counted = COUNTED.flatMap { category ->
                (1..count(unit, category)).map { RequiredPhoto(unitId, unit.unitNumber, it, category) }
            }
            counted.ifEmpty { listOf(RequiredPhoto(unitId, unit.unitNumber, 1, insideCategory)) }
        }
    }

    /**
     * Still to shoot. A photo counts for a need when the category matches and (several flats) the
     * flat matches. Photos with a slot fill that slot; photos the server returned without a slot
     * fill the remaining slots in order — so "1 bedroom photo uploaded" is never asked again.
     */
    fun missing(property: Property, frontView: Boolean = false, insideCategory: String = LIVING_ROOM): List<RequiredPhoto> =
        required(property, frontView, insideCategory).groupBy { Triple(it.category, it.unitId, it.roomId) }.flatMap { (key, needs) ->
            val (category, unitId, roomId) = key
            // Photos saved without a flat (taken when there was only one flat) belong to the first flat.
            val firstUnitId = property.units.firstOrNull()?.id
            val photos = property.images.filter { img ->
                if (roomId != null) return@filter img.roomId == roomId // any photo of that room counts
                img.category == category &&
                    (unitId == null || img.unitId == unitId || (img.unitId == null && unitId == firstUnitId))
            }
            val slots = photos.mapNotNull { it.slot }.toSet()
            var spare = photos.count { it.slot == null || it.slot !in needs.map { n -> n.slot } }
            needs.sortedBy { it.slot }.filter { need ->
                when {
                    need.slot in slots -> false
                    spare > 0 -> { spare--; false }
                    else -> true
                }
            }
        }

    private const val MAX_PER_CATEGORY = 20
}

/** Apartment = building + separately rented flats (pure, unit-tested). */
object ApartmentRules {
    /** The flat's "pricing" object sent with POST / PATCH units: only the price fields, blanks dropped. */
    fun flatPricingBody(values: Map<String, String>, listingType: String): Map<String, String> =
        PropertyForms.flatPricing(listingType).mapNotNull { f -> values[f.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { f.key to it } }.toMap()

    /** Flats still missing their price (highlighted on the Flats step). */
    fun flatsWithoutPrice(units: List<PropertyUnit>, listingType: String): List<PropertyUnit> {
        val key = if (listingType == "SALE") "salePrice" else "monthlyRent"
        return units.filter { u -> u.values[key]?.toDoubleOrNull()?.let { it > 0 } != true }
    }

    /** PG rooms without a price: no rent per bed and no bed with its own rent. */
    fun roomsWithoutPrice(rooms: List<PropertyRoom>): List<PropertyRoom> = rooms.filter { r ->
        r.values["rentPerBed"]?.toDoubleOrNull()?.let { it > 0 } != true &&
            r.beds.none { b -> b.rent?.toDoubleOrNull()?.let { it > 0 } == true }
    }

    /** Submit problem → wizard step; unit / room priced: rent / deposit / price problems are fixed on the units / rooms. */
    fun stepFor(problem: String, apartment: Boolean): Int {
        val p = problem.lowercase()
        return if (apartment && listOf("rent", "deposit", "price").any { it in p } && "charge" !in p) WizardStep.UNITS
        else SubmitProblems.stepFor(problem)
    }

    /** Building photo categories (no unitId) for an apartment. */
    val BUILDING_CATEGORIES = listOf("FRONT_VIEW", "ENTRANCE", "BUILDING_EXTERIOR", "PARKING", "COMMON_AREA", "OTHER")
}

/** Flat amenity chip: tap cycles not set → YES → NO → NOT_DISCLOSED → not set. */
object FlatAmenityCycle {
    fun next(current: String?): String? = when (current?.uppercase()) {
        null, "" -> "YES"
        "YES" -> "NO"
        "NO" -> "NOT_DISCLOSED"
        else -> null
    }
}
