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
    /** Who owns the property (private). Last step: "Submit for verification" is here. Old drafts saved at 10 resume here too. */
    const val OWNERSHIP = 10
    const val LAST = OWNERSHIP
    const val COUNT = 10
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

/** Which fields of a form are shown right now ([ShownWhen]); hidden ones are cleared before saving. */
object FormVisibility {
    fun isShown(field: FieldDef, values: Map<String, String>, all: List<FieldDef>): Boolean {
        val rule = field.shownWhen ?: return true
        // A child of a hidden field is hidden too (meter number under "no separate meter").
        all.firstOrNull { it.key == rule.field }?.let { parent -> if (!isShown(parent, values, all)) return false }
        val v = values[rule.field].orEmpty().trim().uppercase()
        return when {
            rule.anyOf.isNotEmpty() -> v in rule.anyOf
            rule.noneOf.isNotEmpty() -> v !in rule.noneOf
            else -> v.isNotEmpty()
        }
    }

    fun visible(fields: List<FieldDef>, values: Map<String, String>): List<FieldDef> = fields.filter { isShown(it, values, fields) }

    /** Only the answers of shown fields (anything else is dropped, so it's never sent). */
    fun clean(fields: List<FieldDef>, values: Map<String, String>): Map<String, String> {
        val keys = visible(fields, values).map { it.key }.toSet()
        return values.filterKeys { it in keys }
    }

    /** Shown fields with no answer → "x answers missing — tenants see 'Not disclosed'". */
    fun unanswered(fields: List<FieldDef>, values: Map<String, String>): Int =
        visible(fields, values).count { values[it.key].isNullOrBlank() }
}

/** Validation for the dynamic forms (building, units, rooms, pricing…). Values are strings. */
object FieldValidator {

    fun isRequired(field: FieldDef, values: Map<String, String>): Boolean {
        val rule = field.requiredWhen ?: return field.required
        val v = values[rule.field].orEmpty().trim()
        if (rule.equals == "*") return v.isNotEmpty()
        return v.equals(rule.equals, ignoreCase = true) || rule.orEquals.any { it.equals(v, ignoreCase = true) }
    }

    fun validate(fields: List<FieldDef>, values: Map<String, String>): Map<String, FieldError> = buildMap {
        // Hidden fields (ShownWhen) are never checked — they're not sent.
        for (field in FormVisibility.visible(fields, values)) {
            val value = values[field.key].orEmpty().trim()
            if (value.isEmpty()) {
                if (isRequired(field, values)) put(field.key, FieldError.Required)
                continue
            }
            field.maxLength?.let { if (value.length > it) put(field.key, FieldError.TooLong(it)) }
            if (field.digitsOnly && !value.all(Char::isDigit)) put(field.key, FieldError.NotANumber)
            else if (field.digitsOnly && !field.type.numeric) {
                // Digits kept as text (water hours …): still respect min / max.
                val n = value.toDoubleOrNull()
                if (n != null && field.max != null && n > field.max) put(field.key, FieldError.AboveMax(field.max))
                if (n != null && field.min != null && n < field.min) put(field.key, FieldError.BelowMin(field.min))
            }
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
            // "Upload the Power of Attorney document for the representative." → Ownership, not Documents.
            listOf("power of attorney", "representative", "co-owner", "co owner", "ownership details", "owner share", "your share", "company name").any { it in p } -> WizardStep.OWNERSHIP
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
    private const val WATER_HOURS = "waterAvailability"
    private val PARKING_KEY = Regex("parking", RegexOption.IGNORE_CASE)
    private val PARKING_COUNT = Regex("slot|count|number|spaces|total", RegexOption.IGNORE_CASE)

    /** Building step: parking isn't asked here (it is asked per unit). */
    fun hiddenInBuilding(field: FieldDef): Boolean = PARKING_KEY.containsMatchIn(field.key)

    fun adjust(field: FieldDef): FieldDef = when {
        field.key in DIGIT_KEYS -> field.copy(type = FieldType.TEXT, digitsOnly = true)
        // Water availability = hours a day → numbers keyboard, 0 to 24 (sent as text, like the server expects).
        field.key == WATER_HOURS -> field.copy(type = FieldType.TEXT, digitsOnly = true, maxLength = 2, min = 0.0, max = 24.0)
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
    @Suppress("UNUSED_PARAMETER")
    fun flatsWithoutPrice(units: List<PropertyUnit>, listingType: String): List<PropertyUnit> =
        units.filter { u -> u.values["monthlyRent"]?.toDoubleOrNull()?.let { it > 0 } != true }

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

/**
 * Flat / unit availability (no "Availability" dropdown any more):
 * - "Available from" date (default + min = today) → tenants see "Available now" / "Available from <date>".
 * - Optional "Not ready to rent yet" switch → UNDER_MAINTENANCE / RESERVED / NOT_AVAILABLE.
 * - OCCUPIED only comes from a lease: never sent, and an occupied unit sends neither field.
 * The form keeps its state in the unit editor's values under [FROM], [STATUS] and [NOT_READY].
 */
object UnitAvailability {
    const val STATUS = "availabilityStatus"
    const val FROM = "availableFrom"
    /** App-only key ("true" / ""): the switch. Never sent. */
    const val NOT_READY = "_notReady"
    const val OCCUPIED = "OCCUPIED"
    val NOT_READY_STATUSES = listOf("UNDER_MAINTENANCE", "RESERVED", "NOT_AVAILABLE")

    /** The server's unit fields minus the two we draw ourselves. */
    fun formFields(fields: List<FieldDef>): List<FieldDef> = fields.filter { it.key != STATUS && it.key != FROM }

    /** How the two fields are typed when sent (date as "YYYY-MM-DD", status as text). */
    val sentFields = listOf(FieldDef(FROM, FROM, FieldType.DATE), FieldDef(STATUS, STATUS, FieldType.SELECT, options = listOf("AVAILABLE") + NOT_READY_STATUSES))

    fun isOccupied(values: Map<String, String>) = values[STATUS].equals(OCCUPIED, true)

    /**
     * Editor values for a new (empty [values]) or existing unit: "Available from" = the unit's date
     * (a past date or none → today), switch on when the status is one of the "not ready" ones.
     */
    fun prefill(values: Map<String, String>, today: SimpleDate): Map<String, String> {
        if (isOccupied(values)) return values
        val date = SimpleDate.parse(values[FROM].orEmpty().take(10))?.takeIf { it >= today } ?: today
        val status = values[STATUS].orEmpty().uppercase()
        val notReady = status in NOT_READY_STATUSES
        return values + mapOf(FROM to date.iso(), STATUS to if (notReady) status else "", NOT_READY to if (notReady) "true" else "")
    }

    fun notReady(values: Map<String, String>) = values[NOT_READY] == "true"

    /** Errors keyed like other fields: date before today, or switch on without a reason chip. */
    fun validate(values: Map<String, String>, today: SimpleDate): Map<String, String> {
        if (isOccupied(values)) return emptyMap()
        val errors = mutableMapOf<String, String>()
        val date = SimpleDate.parse(values[FROM].orEmpty())
        if (date == null || date < today) errors[FROM] = ERR_DATE
        if (notReady(values) && values[STATUS].orEmpty().uppercase() !in NOT_READY_STATUSES) errors[STATUS] = ERR_REASON
        return errors
    }

    /**
     * What goes in POST / PATCH unit: availableFrom always (unless occupied);
     * availabilityStatus = the chip when "not ready", "AVAILABLE" on edit (a not-ready flat becomes
     * rentable again), omitted on create. Never OCCUPIED.
     */
    fun body(values: Map<String, String>, isNew: Boolean, today: SimpleDate): Map<String, String> {
        if (isOccupied(values)) return emptyMap()
        val from = SimpleDate.parse(values[FROM].orEmpty())?.takeIf { it >= today } ?: today
        val status = when {
            notReady(values) -> values[STATUS].orEmpty().uppercase().takeIf { it in NOT_READY_STATUSES }
            isNew -> null
            else -> "AVAILABLE"
        }
        return buildMap {
            put(FROM, from.iso())
            status?.let { put(STATUS, it) }
        }
    }

    /** Chip colour group for a unit (status + availableNow / date). */
    enum class Tone { NOW, LATER, OCCUPIED, OTHER }

    fun tone(status: String, availableNow: Boolean?, availableFrom: String, today: SimpleDate): Tone = when (status.uppercase()) {
        OCCUPIED -> Tone.OCCUPIED
        "AVAILABLE", "" -> {
            val now = availableNow ?: (SimpleDate.parse(availableFrom.take(10))?.let { it <= today } ?: true)
            if (now) Tone.NOW else Tone.LATER
        }
        else -> Tone.OTHER
    }

    const val ERR_DATE = "@unit_date"
    const val ERR_REASON = "@unit_reason"
}

/** SafeRent is for rentals only (rent / lease) — never "for sale". */
object ListingTypes {
    const val DEFAULT = "RENT"
    val RENTAL = listOf("RENT", "LEASE")

    /** Server list without SALE (or anything not a rental); empty → just RENT. */
    fun rentalOnly(types: List<String>?): List<String> =
        types.orEmpty().map { it.uppercase() }.filter { it in RENTAL }.distinct().ifEmpty { listOf(DEFAULT) }

    /** A saved / restored value that isn't a rental (e.g. old "SALE") → RENT / null. */
    fun safe(type: String?): String = type?.uppercase()?.takeIf { it in RENTAL } ?: DEFAULT
}
