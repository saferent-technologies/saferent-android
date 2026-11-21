package com.saferenttechnologies.android.domain.model

/** How step 5 works for a property type. */
enum class PropertyStructure {
    MULTI_UNIT, VILLA, SINGLE_UNIT, DUPLEX, ROOMS_BEDS;

    val usesRooms: Boolean get() = this == ROOMS_BEDS

    /** One unit, one form, no list. */
    val singleUnit: Boolean get() = this == SINGLE_UNIT || this == VILLA

    companion object {
        fun of(value: String?): PropertyStructure =
            entries.firstOrNull { it.name == value?.trim()?.uppercase() } ?: MULTI_UNIT
    }
}

/** Dynamic field widget types sent by the server. */
enum class FieldType {
    TEXT, TEXTAREA, INTEGER, DECIMAL, MONEY, BOOLEAN, DISCLOSURE, SELECT, DATE;

    val numeric: Boolean get() = this == INTEGER || this == DECIMAL || this == MONEY

    companion object {
        fun of(value: String?): FieldType = entries.firstOrNull { it.name == value?.trim()?.uppercase() } ?: TEXT
    }
}

/** `requiredWhen: { field, equals }` → required only when that field has that value. */
/** Required when [field] equals [equals] (or any of [orEquals]); [equals] = "*" → when [field] is filled in. */
data class RequiredWhen(val field: String, val equals: String, val orEquals: Set<String> = emptySet())

/**
 * Show a field only when its parent answer needs it: [anyOf] = parent is one of these; [noneOf] = parent is
 * not one of these (blank counts as "not"); both empty = parent is filled in. Hidden fields are never sent.
 */
data class ShownWhen(val field: String, val anyOf: Set<String> = emptySet(), val noneOf: Set<String> = emptySet())

data class FieldDef(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    val maxLength: Int? = null,
    val min: Double? = null,
    val max: Double? = null,
    val options: List<String> = emptyList(),
    val requiredWhen: RequiredWhen? = null,
    /** Text that may only hold digits (flat number) → number keyboard, digits only. */
    val digitsOnly: Boolean = false,
    val shownWhen: ShownWhen? = null,
)

data class PropertyTypeDef(
    val key: String,
    val label: String,
    val category: String,
    val structure: PropertyStructure,
    val enabled: Boolean,
    val unitKind: String = "",
    val unitLabel: String = "",
    val listingTypes: List<String> = emptyList(),
    val floorWise: Boolean = false,
    val buildingFields: List<FieldDef> = emptyList(),
    val unitFields: List<FieldDef> = emptyList(),
) {
    /** Apartment / flat types → the units step is called "Flats". */
    val isFlat: Boolean
        get() = listOf(key, category, unitKind, unitLabel).any { it.contains("FLAT", ignoreCase = true) || it.contains("APARTMENT", ignoreCase = true) }
}

data class PropertyCategory(val key: String, val label: String, val types: List<PropertyTypeDef>)

data class WizardStepDef(val step: Int, val key: String, val title: String)

/** GET property-types. [enums] = every enum list by name (listingTypes, disclosure, imageCategories…). */
data class PropertyTypes(
    val categories: List<PropertyCategory>,
    val wizardSteps: List<WizardStepDef>,
    val enums: Map<String, List<String>>,
) {
    fun type(key: String?): PropertyTypeDef? = categories.flatMap { it.types }.firstOrNull { it.key == key }
    fun enum(name: String, fallback: List<String> = emptyList()): List<String> = enums[name]?.takeIf { it.isNotEmpty() } ?: fallback
}

data class Bed(val id: String, val bedNumber: String, val availabilityStatus: String = "", val rent: String? = null, val version: Int = 1)

data class PropertyUnit(
    val id: String,
    val code: String = "",
    val version: Int = 1,
    val unitNumber: String = "",
    /** All unit fields as strings (dynamic form values; pricing { monthlyRent … } flattened in). */
    val values: Map<String, String> = emptyMap(),
    /** Amenities inside this flat: key → YES / NO / NOT_DISCLOSED. */
    val amenities: Map<String, String> = emptyMap(),
    val imageIds: List<String> = emptyList(),
)

data class PropertyRoom(
    val id: String,
    val roomNumber: String = "",
    val version: Int = 1,
    val values: Map<String, String> = emptyMap(),
    val beds: List<Bed> = emptyList(),
    /** Amenities inside this room: key → YES / NO / NOT_DISCLOSED. */
    val amenities: Map<String, String> = emptyMap(),
    val imageIds: List<String> = emptyList(),
)

data class PropertyImage(
    val id: String,
    val category: String,
    val slot: Int? = null,
    val status: String = "",
    val url: String = "",
    val displayOrder: Int? = null,
    val unitId: String? = null,
    val roomId: String? = null,
)

data class PropertyDocument(
    val id: String,
    val code: String = "",
    val docType: String,
    val status: String = "",
    val originalName: String = "",
)

data class CompletenessSection(val key: String, val label: String, val score: Int, val missing: List<String>)

data class Completeness(val score: Int = 0, val sections: List<CompletenessSection> = emptyList(), val note: String = "")

data class Property(
    val id: String,
    val code: String = "",
    val version: Int = 1,
    val propertyType: String = "",
    val propertyTypeLabel: String = "",
    val structure: PropertyStructure = PropertyStructure.MULTI_UNIT,
    val listingType: String = "RENT",
    val title: String = "",
    val description: String = "",
    val addressLine: String = "",
    val locality: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val building: Map<String, String> = emptyMap(),
    val pricing: Map<String, String> = emptyMap(),
    val charges: List<Charge> = emptyList(),
    val units: List<PropertyUnit> = emptyList(),
    val rooms: List<PropertyRoom> = emptyList(),
    val images: List<PropertyImage> = emptyList(),
    val videos: List<PropertyVideo> = emptyList(),
    /** The property response had a "videos" list. false → server can't report videos yet, so the app can't check them. */
    val videosFromServer: Boolean = true,
    val documents: List<PropertyDocument> = emptyList(),
    val publicationStatus: String = "DRAFT",
    val verificationStatus: String = "",
    val wizardStep: Int = 1,
    val completeness: Completeness = Completeness(),
    // ---- manage / detail ----
    val activeStatus: String = "ACTIVE",
    val moderationNote: String = "",
    val rentFrom: String = "",
    val rentTo: String = "",
    val availableCount: Int = 0,
    val occupiedCount: Int = 0,
    val coverImageId: String? = null,
    val amenities: List<Pair<String, String>> = emptyList(),
)

/** Reverse-geocoded address (GET geo/reverse). */
data class GeoAddress(
    val addressLine: String = "",
    val locality: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val formattedAddress: String = "",
)

/** One GPS fix. [accuracyMeters] > 50 → ask to move near a window and retry. */
data class GeoFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float)

/** A charge on the rent step. Money values are strings in rupees. */
data class Charge(val kind: String, val billing: String, val amount: String = "", val frequency: String = "")

/** Amenity catalog (GET amenities). */
data class AmenityGroup(val key: String, val label: String, val items: List<AmenityItem>)
data class AmenityItem(val key: String, val label: String)

/** Only property type "APARTMENT" uses the building + flats rules. */
fun isApartmentType(propertyType: String?): Boolean = propertyType.equals("APARTMENT", ignoreCase = true)

/**
 * Occupancy is set by leases on the live listing. The wizard's change-request copy may still say
 * OCCUPIED after a lease closed → copy the live statuses on (match flats by code / number, beds by
 * room number + bed number). Once the lease is closed the flat / room is fully editable again.
 */
object Occupancy {
    private fun unitKey(u: PropertyUnit) = u.code.ifBlank { u.unitNumber }.trim().lowercase()
    private fun roomKey(r: PropertyRoom) = r.roomNumber.trim().lowercase()

    fun fromLive(draft: Property, live: Property): Property {
        val unitStatus = live.units.filter { unitKey(it).isNotBlank() }.associate { unitKey(it) to it.values["availabilityStatus"].orEmpty() }
        val bedStatus = live.rooms.associate { r -> roomKey(r) to r.beds.associate { it.bedNumber.trim().lowercase() to it.availabilityStatus } }
        return draft.copy(
            units = draft.units.map { u ->
                val liveStatus = unitStatus[unitKey(u)] ?: return@map u
                val mine = u.values["availabilityStatus"].orEmpty()
                when {
                    // Lease closed → not occupied any more; use the live status (AVAILABLE …).
                    mine.equals("OCCUPIED", true) && !liveStatus.equals("OCCUPIED", true) ->
                        u.copy(values = u.values + ("availabilityStatus" to liveStatus.ifBlank { "AVAILABLE" }))
                    // New lease since the copy → lock it.
                    liveStatus.equals("OCCUPIED", true) -> u.copy(values = u.values + ("availabilityStatus" to "OCCUPIED"))
                    else -> u
                }
            },
            rooms = draft.rooms.map { r ->
                val beds = bedStatus[roomKey(r)] ?: return@map r
                r.copy(
                    beds = r.beds.map { b ->
                        val liveStatus = beds[b.bedNumber.trim().lowercase()] ?: return@map b
                        when {
                            b.availabilityStatus.equals("OCCUPIED", true) && !liveStatus.equals("OCCUPIED", true) -> b.copy(availabilityStatus = liveStatus.ifBlank { "AVAILABLE" })
                            liveStatus.equals("OCCUPIED", true) -> b.copy(availabilityStatus = "OCCUPIED")
                            else -> b
                        }
                    },
                )
            },
        )
    }
}

/** A tenant lives here (lease) → the flat can't be edited, re-priced or deleted in the wizard. */
val PropertyUnit.isOccupied: Boolean get() = values["availabilityStatus"].equals("OCCUPIED", true)

/** Every bed has a tenant → the room (details, rent, photos, beds) is locked in the wizard. */
val PropertyRoom.isFullyOccupied: Boolean get() = beds.isNotEmpty() && beds.all { it.availabilityStatus.equals("OCCUPIED", true) }

/** Any occupied flat / bed → the property can't be archived and those units can't be deleted. */
val Property.hasTenants: Boolean get() = units.any { it.isOccupied } || rooms.any { r -> r.beds.any { it.availabilityStatus.equals("OCCUPIED", true) } }

/** Photos of the building itself: not tagged to a flat / room, and not listed in any flat's / room's imageIds. */
object BuildingPhotos {
    fun of(p: Property): List<PropertyImage> {
        val inside = (p.units.flatMap { it.imageIds } + p.rooms.flatMap { it.imageIds }).toSet()
        return p.images.filter { it.unitId.isNullOrBlank() && it.roomId.isNullOrBlank() && it.id !in inside }
    }
}
