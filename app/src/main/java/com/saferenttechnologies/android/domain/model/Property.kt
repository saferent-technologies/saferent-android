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
data class RequiredWhen(val field: String, val equals: String)

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
