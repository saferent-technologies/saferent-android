package com.saferenttechnologies.android.data.mapper

import com.saferenttechnologies.android.domain.model.BuildingLimits
import com.saferenttechnologies.android.data.remote.dto.CompletenessDto
import com.saferenttechnologies.android.data.remote.dto.FieldDefDto
import com.saferenttechnologies.android.data.remote.dto.PropertyDocumentDto
import com.saferenttechnologies.android.data.remote.dto.PropertyDto
import com.saferenttechnologies.android.data.remote.dto.PropertyImageDto
import com.saferenttechnologies.android.data.remote.dto.PropertyVideoDto
import com.saferenttechnologies.android.data.remote.dto.WizardPropertyTypeDto
import com.saferenttechnologies.android.data.remote.dto.PropertyTypesDto
import com.saferenttechnologies.android.domain.model.AmenityGroup
import com.saferenttechnologies.android.domain.model.AmenityItem
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.Bed
import com.saferenttechnologies.android.domain.model.Charge
import com.saferenttechnologies.android.domain.model.Completeness
import com.saferenttechnologies.android.domain.model.CompletenessSection
import com.saferenttechnologies.android.domain.model.FieldDef
import com.saferenttechnologies.android.domain.model.FieldOverrides
import com.saferenttechnologies.android.domain.model.FieldType
import com.saferenttechnologies.android.domain.model.Property
import com.saferenttechnologies.android.domain.model.PropertyCategory
import com.saferenttechnologies.android.domain.model.PropertyDocument
import com.saferenttechnologies.android.domain.model.PropertyImage
import com.saferenttechnologies.android.domain.model.PropertyRoom
import com.saferenttechnologies.android.domain.model.PropertyStructure
import com.saferenttechnologies.android.domain.model.PropertyTypeDef
import com.saferenttechnologies.android.domain.model.PropertyTypes
import com.saferenttechnologies.android.domain.model.PropertyUnit
import com.saferenttechnologies.android.domain.model.RequiredWhen
import com.saferenttechnologies.android.domain.model.WizardStepDef
import com.saferenttechnologies.android.domain.repository.TypedValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// ---------- property types ----------

internal fun JsonElement.asText(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> contentOrNull
    is JsonObject -> (this["value"] ?: this["key"])?.asText()
    else -> null
}

private fun JsonElement.asStringList(): List<String> =
    (this as? JsonArray)?.mapNotNull { it.asText()?.takeIf { s -> s.isNotBlank() && s != "..." } }.orEmpty()

fun FieldDefDto.toDomain() = FieldDef(
    key = key,
    label = label.ifBlank { key },
    type = FieldType.of(type),
    required = required,
    maxLength = maxLength,
    min = min,
    max = max,
    options = options.mapNotNull { it.asText() },
    requiredWhen = requiredWhen?.takeIf { it.field.isNotBlank() }?.let { RequiredWhen(it.field, it.equals?.asText().orEmpty()) },
)

fun WizardPropertyTypeDto.toDomain(categoryKey: String) = PropertyTypeDef(
    key = key,
    label = label.ifBlank { key },
    category = category.ifBlank { categoryKey },
    structure = PropertyStructure.of(structure),
    enabled = enabled,
    unitKind = unitKind,
    unitLabel = unitLabel,
    listingTypes = listingTypes,
    floorWise = floorWise,
    buildingFields = buildingFields.filter { it.key.isNotBlank() }.map { it.toDomain() }
        .filterNot(FieldOverrides::hiddenInBuilding).map(FieldOverrides::adjust),
    unitFields = unitFields.filter { it.key.isNotBlank() }.map { FieldOverrides.adjust(it.toDomain()) },
).let { t -> if (t.isFlat) t.copy(buildingFields = t.buildingFields.map(BuildingLimits::forFlats)) else t }

/** Only enabled types; categories without any enabled type are dropped. */
fun PropertyTypesDto.toDomain() = PropertyTypes(
    categories = categories.mapNotNull { c ->
        val types = c.types.filter { it.key.isNotBlank() && it.enabled }.map { it.toDomain(c.key) }
        types.takeIf { it.isNotEmpty() }?.let { PropertyCategory(c.key, c.label.ifBlank { c.key }, it) }
    },
    wizardSteps = wizardSteps.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val step = (o["step"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        WizardStepDef(step, o["key"]?.asText().orEmpty(), o["title"]?.asText().orEmpty())
    },
    enums = enums.mapValues { it.value.asStringList() }.filterValues { it.isNotEmpty() },
)

// ---------- property ----------

/** Primitive fields of an object → strings (nested objects / arrays skipped). */
internal fun JsonObject.primitives(skip: Set<String> = emptySet()): Map<String, String> =
    entries.mapNotNull { (k, v) ->
        if (k in skip) null else (v as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.let { k to it }
    }.toMap()

internal fun JsonObject.text(key: String): String = this[key]?.asText().orEmpty()
internal fun JsonObject.int(key: String, default: Int): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: default

fun PropertyImageDto.toDomain() = PropertyImage(id, category, slot, status, url, displayOrder, unitId, roomId)

fun PropertyVideoDto.toDomain() = com.saferenttechnologies.android.domain.model.PropertyVideo(
    id = id,
    url = url.ifBlank { "/api/v1/files/property-videos/$id" },
    thumbnailUrl = thumbnailUrl.orEmpty(),
    durationSec = durationSec?.toInt() ?: 0,
    status = status.ifBlank { "PENDING" },
    unitId = unitId?.ifBlank { null },
    roomId = roomId?.ifBlank { null },
    rejectionReason = rejectionReason.orEmpty(),
)

fun PropertyDocumentDto.toDomain() = PropertyDocument(id, code, docType, status, originalName.orEmpty())

fun CompletenessDto.toDomain() = Completeness(
    score = score,
    sections = sections.map { CompletenessSection(it.key, it.label.ifBlank { it.key }, it.score, it.missing) },
    note = note.orEmpty(),
)

fun PropertyDto.toDomain(): Property {
    val pricingObj = pricing
    val charges = (pricingObj?.get("charges") as? JsonArray)?.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        Charge(o.text("kind"), o.text("billing"), o.text("amount"), o.text("frequency"))
    }.orEmpty()
    return Property(
        id = id,
        code = code,
        version = version,
        propertyType = propertyType,
        propertyTypeLabel = propertyTypeLabel,
        structure = PropertyStructure.of(structure),
        listingType = listingType,
        title = title.orEmpty(),
        description = description.orEmpty(),
        addressLine = addressLine.orEmpty(),
        locality = locality.orEmpty(),
        city = city.orEmpty(),
        state = state.orEmpty(),
        pincode = pincode.orEmpty(),
        latitude = latitude?.contentOrNull?.toDoubleOrNull(),
        longitude = longitude?.contentOrNull?.toDoubleOrNull(),
        building = building?.primitives().orEmpty(),
        pricing = pricingObj?.primitives(skip = setOf("charges")).orEmpty(),
        charges = charges,
        units = units.map { u ->
            // attributes { bhkType } and pricing { monthlyRent } are flattened into the values.
            val nested = (u["attributes"] as? JsonObject)?.primitives().orEmpty() +
                (u["pricing"] as? JsonObject)?.primitives().orEmpty() +
                // occupancy { tenantName, since, leaseEnds, movingOutOn } → "occupancy.tenantName" …
                (u["occupancy"] as? JsonObject)?.primitives().orEmpty().mapKeys { "occupancy.${it.key}" }
            PropertyUnit(
                id = u.text("id"),
                code = u.text("code"),
                version = u.int("version", 1),
                unitNumber = u.text("unitNumber"),
                values = nested + u.primitives(skip = setOf("id", "code", "version", "createdAt", "updatedAt")),
                amenities = (u["amenities"] as? JsonArray)?.mapNotNull { a ->
                    val o = a as? JsonObject ?: return@mapNotNull null
                    o.text("key").ifBlank { o.text("label") }.ifBlank { null }?.let { it to o.text("status").ifBlank { "YES" } }
                }?.toMap().orEmpty(),
                imageIds = (u["imageIds"] as? JsonArray)?.mapNotNull { it.asText() }.orEmpty(),
            )
        },
        rooms = rooms.map { r ->
            PropertyRoom(
                id = r.text("id"),
                roomNumber = r.text("roomNumber"),
                version = r.int("version", 1),
                values = r.primitives(skip = setOf("id", "code", "version", "createdAt", "updatedAt")),
                beds = (r["beds"] as? JsonArray)?.mapNotNull { b ->
                    val o = b as? JsonObject ?: return@mapNotNull null
                    Bed(o.text("id"), o.text("bedNumber"), o.text("availabilityStatus"), o["rent"]?.asText(), o.int("version", 1))
                }.orEmpty(),
                amenities = (r["amenities"] as? JsonArray)?.mapNotNull { a ->
                    val o = a as? JsonObject ?: return@mapNotNull null
                    o.text("key").ifBlank { o.text("label") }.ifBlank { null }?.let { it to o.text("status").ifBlank { "YES" } }
                }?.toMap().orEmpty(),
                imageIds = (r["imageIds"] as? JsonArray)?.mapNotNull { it.asText() }.orEmpty(),
            )
        },
        images = images.map { it.toDomain() },
        videos = videos.orEmpty().filter { it.id.isNotBlank() }.map { it.toDomain() },
        videosFromServer = videos != null,
        documents = documents.map { it.toDomain() },
        publicationStatus = publicationStatus,
        verificationStatus = verificationStatus.orEmpty(),
        wizardStep = wizardStep,
        completeness = completeness?.toDomain() ?: Completeness(),
        activeStatus = activeStatus ?: "ACTIVE",
        moderationNote = moderationNote.orEmpty(),
        rentFrom = rentFrom?.contentOrNull.orEmpty(),
        rentTo = rentTo?.contentOrNull.orEmpty(),
        availableCount = availableCount,
        occupiedCount = occupiedCount,
        coverImageId = coverImageId,
        amenities = amenities.map { (it.text("label").ifBlank { it.text("key") }) to it.text("status") },
    )
}

/** `data` as an array, or `data.<one of keys>` as an array. */
fun JsonElement.arrayAt(vararg keys: String): List<JsonElement> = when (this) {
    is JsonArray -> this
    is JsonObject -> keys.firstNotNullOfOrNull { this[it] as? JsonArray }.orEmpty()
    else -> emptyList()
}

/** GET amenities → groups. Accepts { groups: [{ key, label, items: [{ key, label }] }] } or a flat list. */
fun JsonElement.toAmenityGroups(): List<AmenityGroup> {
    fun item(e: JsonElement): AmenityItem? {
        val o = e as? JsonObject ?: return e.asText()?.let { AmenityItem(it, it) }
        val key = o.text("key").ifBlank { return null }
        return AmenityItem(key, o.text("label").ifBlank { key })
    }
    val groups = arrayAt("groups", "amenities", "items")
    if (groups.all { (it as? JsonObject)?.get("items") is JsonArray }) {
        return groups.mapNotNull { g ->
            val o = g as? JsonObject ?: return@mapNotNull null
            AmenityGroup(o.text("key"), o.text("label").ifBlank { o.text("key") }, (o["items"] as JsonArray).mapNotNull(::item))
        }.filter { it.items.isNotEmpty() }
    }
    val items = groups.mapNotNull(::item)
    return if (items.isEmpty()) emptyList() else listOf(AmenityGroup("ALL", "", items))
}

// ---------- requests ----------

/** Form values → JSON with the right types; blank values are left out. */
fun TypedValues.toJsonMap(): Map<String, JsonElement> = buildMap {
    val types = fields.associate { it.key to it.type }
    // this@toJsonMap: inside buildMap, plain `values` would be the map being built.
    for ((key, raw) in this@toJsonMap.values) {
        val value = raw.trim()
        if (value.isEmpty()) continue
        val element: JsonElement = when (types[key]) {
            FieldType.INTEGER -> value.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
            FieldType.DECIMAL -> value.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
            FieldType.BOOLEAN -> JsonPrimitive(value.equals("true", ignoreCase = true))
            else -> JsonPrimitive(value) // text, money (string rupees), select, disclosure, date
        }
        put(key, element)
    }
}

// ---------- errors ----------

/**
 * 400 VALIDATION_ERROR (details.issues[{ path, message }]) → [AppError.FieldIssues]
 * 409 → [AppError.Conflict] (VERSION_CONFLICT = reload)
 * 412 INVALID_STATE (details.problems[]) → [AppError.InvalidState]
 */
fun AppError.toPropertyError(json: Json): AppError {
    if (this !is AppError.Server) return this
    val detailsObj = details?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
    return when (httpCode) {
        400 -> {
            val issues = (detailsObj?.get("issues") as? JsonArray)?.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val path = o.text("path").substringAfterLast('.').ifBlank { return@mapNotNull null }
                path to o.text("message").ifBlank { message }
            }?.toMap().orEmpty()
            AppError.FieldIssues(message, issues)
        }
        409 -> AppError.Conflict(
            message,
            versionConflict = reason == "VERSION_CONFLICT" || errorCode == "VERSION_CONFLICT",
        )
        412 -> AppError.InvalidState(
            message,
            (detailsObj?.get("problems") as? JsonArray)?.mapNotNull { it.asText() }.orEmpty(),
            errorCode = errorCode ?: reason,
            // VIDEO_REQUIRED → the flats without a video.
            unitIds = (detailsObj?.get("unitIds") as? JsonArray)?.mapNotNull { it.asText() }.orEmpty(),
        )
        else -> this
    }
}
