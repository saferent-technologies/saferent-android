package com.saferenttechnologies.android.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Every field nullable / defaulted: the server may add, drop or null anything.

@Serializable
data class RequiredWhenDto(val field: String = "", val equals: JsonElement? = null)

@Serializable
data class FieldDefDto(
    val key: String = "",
    val label: String = "",
    val type: String = "text",
    val required: Boolean = false,
    val maxLength: Int? = null,
    val min: Double? = null,
    val max: Double? = null,
    /** Strings, or { value, label } objects. */
    val options: List<JsonElement> = emptyList(),
    val requiredWhen: RequiredWhenDto? = null,
)

@Serializable
data class WizardPropertyTypeDto(
    val key: String = "",
    val label: String = "",
    val category: String = "",
    val structure: String = "",
    val enabled: Boolean = true,
    val unitKind: String = "",
    val unitLabel: String = "",
    val listingTypes: List<String> = emptyList(),
    val floorWise: Boolean = false,
    val buildingFields: List<FieldDefDto> = emptyList(),
    val unitFields: List<FieldDefDto> = emptyList(),
)

@Serializable
data class PropertyCategoryDto(val key: String = "", val label: String = "", val types: List<WizardPropertyTypeDto> = emptyList())

@Serializable
data class WizardStepDto(val step: Int = 0, val key: String = "", val title: String = "")

@Serializable
data class PropertyTypesDto(
    val categories: List<PropertyCategoryDto> = emptyList(),
    /** Entries may be objects or placeholders → parsed leniently. */
    val wizardSteps: List<JsonElement> = emptyList(),
    val enums: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class PropertyImageDto(
    val id: String = "",
    val category: String = "",
    val slot: Int? = null,
    val status: String = "",
    val url: String = "",
    val displayOrder: Int? = null,
    val unitId: String? = null,
    val roomId: String? = null,
)

@Serializable
data class PropertyVideoDto(
    val id: String = "",
    val url: String = "",
    val thumbnailUrl: String? = null,
    /** Number (42 or 42.0). */
    val durationSec: Double? = null,
    val status: String = "",
    val unitId: String? = null,
    val roomId: String? = null,
    val rejectionReason: String? = null,
)

@Serializable
data class PropertyDocumentDto(
    val id: String = "",
    val code: String = "",
    val docType: String = "",
    val status: String = "",
    val originalName: String? = null,
)

@Serializable
data class CompletenessSectionDto(val key: String = "", val label: String = "", val score: Int = 0, val missing: List<String> = emptyList())

@Serializable
data class CompletenessDto(val score: Int = 0, val sections: List<CompletenessSectionDto> = emptyList(), val note: String? = null)

@Serializable
data class PropertyDto(
    val id: String = "",
    val code: String = "",
    val version: Int = 1,
    val propertyType: String = "",
    val propertyTypeLabel: String = "",
    val structure: String = "",
    val listingType: String = "RENT",
    val title: String? = null,
    val description: String? = null,
    val addressLine: String? = null,
    val locality: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    /** Number or string. */
    val latitude: JsonPrimitive? = null,
    val longitude: JsonPrimitive? = null,
    val building: JsonObject? = null,
    val pricing: JsonObject? = null,
    val units: List<JsonObject> = emptyList(),
    val rooms: List<JsonObject> = emptyList(),
    val images: List<PropertyImageDto> = emptyList(),
    /** null = the server didn't send "videos" (videos not supported there yet). */
    val videos: List<PropertyVideoDto>? = null,
    val documents: List<PropertyDocumentDto> = emptyList(),
    val publicationStatus: String = "DRAFT",
    val verificationStatus: String? = null,
    val wizardStep: Int = 1,
    val completeness: CompletenessDto? = null,
    val activeStatus: String? = null,
    val moderationNote: String? = null,
    val rentFrom: JsonPrimitive? = null,
    val rentTo: JsonPrimitive? = null,
    val availableCount: Int = 0,
    val occupiedCount: Int = 0,
    val coverImageId: String? = null,
    val amenities: List<JsonObject> = emptyList(),
)

/** What the signed-in user may do with this property. */
@Serializable
data class PropertyCanDto(
    val manage: Boolean = false,
    val review: Boolean = false,
    val assign: Boolean = false,
    val inspect: Boolean = false,
    val transferOwnership: Boolean = false,
    val editRevision: Boolean = false,
    val submitRevision: Boolean = false,
    val discardRevision: Boolean = false,
)

@Serializable
data class PropertyDataDto(
    val property: PropertyDto? = null,
    val can: PropertyCanDto? = null,
    /** Change request of a live listing (null = none open). */
    val pendingRevision: JsonElement? = null,
    val lastRevision: JsonElement? = null,
    val approval: JsonElement? = null,
)

@Serializable
data class DocumentDataDto(val document: PropertyDocumentDto? = null)

@Serializable
data class DocumentsDataDto(val documents: List<PropertyDocumentDto> = emptyList())

@Serializable
data class ImagesDataDto(val images: List<PropertyImageDto> = emptyList())
