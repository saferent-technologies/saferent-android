package com.saferenttechnologies.android.data.property

import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.toAmenityGroups
import com.saferenttechnologies.android.data.mapper.toDomain
import com.saferenttechnologies.android.data.mapper.toJsonMap
import com.saferenttechnologies.android.data.mapper.toPropertyError
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.PropertyApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.data.remote.dto.PropertyDocumentDto
import com.saferenttechnologies.android.data.remote.dto.PropertyDto
import com.saferenttechnologies.android.domain.model.AmenityGroup
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Completeness
import com.saferenttechnologies.android.domain.model.Property
import com.saferenttechnologies.android.domain.model.PropertyDocument
import com.saferenttechnologies.android.domain.model.PropertyTypes
import com.saferenttechnologies.android.domain.repository.FloorInput
import com.saferenttechnologies.android.domain.repository.PricingInput
import com.saferenttechnologies.android.domain.repository.PropertyPatch
import com.saferenttechnologies.android.domain.repository.PropertyRepository
import com.saferenttechnologies.android.domain.repository.TypedValues
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyRepositoryImpl @Inject constructor(
    private val api: PropertyApi,
    private val safeApiCall: SafeApiCall,
    private val json: Json,
) : PropertyRepository {

    /** Property types are cached in memory (per process) — the wizard reads them often. */
    private var typesCache: PropertyTypes? = null
    private val typesLock = Mutex()

    override suspend fun propertyTypes(): AppResult<PropertyTypes> = typesLock.withLock {
        typesCache?.let { return AppResult.Success(it) }
        call("GET property-types", { api.propertyTypes() }) { it.toDomain() }.also {
            if (it is AppResult.Success) typesCache = it.data
        }
    }

    override suspend fun drafts(): AppResult<List<Property>> =
        call("GET properties?DRAFT", { api.properties("DRAFT") }) { data ->
            data.arrayAt("properties", "items", "data").mapNotNull { e ->
                runCatching { json.decodeFromJsonElement(PropertyDto.serializer(), e).toDomain() }.getOrNull()
            }.filter { it.id.isNotBlank() }
        }

    override suspend fun property(id: String): AppResult<Property> =
        // The wizard always edits the draft view: for a live listing that's its change request
        // (own unit / room / bed / photo ids + version); for drafts it's simply the property.
        call("GET property (draft view)", { api.property(id, DRAFT_VIEW) }) { it.property?.toDomain() }

    override suspend fun create(typeKey: String, listingType: String, title: String?): AppResult<Property> {
        val body = buildJsonObject {
            put("propertyTypeKey", typeKey)
            put("listingType", listingType)
            title?.takeIf { it.isNotBlank() }?.let { put("title", it.trim()) }
        }
        return call("POST properties", { api.create(body) }) { it.property?.toDomain() }
    }

    override suspend fun patch(id: String, patch: PropertyPatch): AppResult<Property> {
        val body = buildJsonObject {
            put("version", patch.version)
            put("wizardStep", patch.wizardStep)
            patch.title?.let { put("title", it.trim()) }
            patch.description?.let { put("description", it.trim()) }
            patch.listingType?.let { put("listingType", it) }
            patch.addressLine?.let { put("addressLine", it.trim()) }
            patch.locality?.let { put("locality", it.trim()) }
            patch.city?.let { put("city", it.trim()) }
            patch.state?.let { put("state", it.trim()) }
            patch.pincode?.let { put("pincode", it.trim()) }
            patch.latitude?.let { put("latitude", round6(it)) }
            patch.longitude?.let { put("longitude", round6(it)) }
            patch.building?.let { put("building", JsonObject(it.toJsonMap())) }
        }
        return call("PATCH property step ${patch.wizardStep}", { api.patch(id, body) }) { it.property?.toDomain() }
    }

    override suspend fun addUnit(id: String, unit: TypedValues, pricing: Map<String, String>) =
        unitCall("POST unit") { api.addUnit(id, JsonObject(unit.toJsonMap() + pricingPart(pricing))) }

    override suspend fun updateUnit(id: String, unitId: String, version: Int, unit: TypedValues, pricing: Map<String, String>) =
        unitCall("PATCH unit") {
            api.updateUnit(id, unitId, JsonObject(unit.toJsonMap() + pricingPart(pricing) + ("version" to JsonPrimitive(version))))
        }

    /** Flat's own price → { "pricing": { "monthlyRent": "18000", … } } (money stays text). */
    private fun pricingPart(pricing: Map<String, String>): Map<String, JsonElement> =
        if (pricing.isEmpty()) emptyMap() else mapOf("pricing" to JsonObject(pricing.mapValues { JsonPrimitive(it.value) }))

    override suspend fun deleteUnit(id: String, unitId: String) = unitCall("DELETE unit") { api.deleteUnit(id, unitId) }

    override suspend fun saveFloors(id: String, unitId: String, floors: List<FloorInput>) = unitCall("PUT unit floors") {
        api.saveFloors(
            id,
            unitId,
            buildJsonObject {
                putJsonArray("floors") {
                    floors.forEach { f ->
                        addJsonObject {
                            put("floorNumber", f.floorNumber)
                            put("bedrooms", f.bedrooms)
                            put("bathrooms", f.bathrooms)
                            put("kitchens", f.kitchens)
                            put("livingRooms", f.livingRooms)
                        }
                    }
                }
            },
        )
    }

    override suspend fun addRoom(id: String, room: TypedValues) =
        unitCall("POST room") { api.addRoom(id, JsonObject(room.toJsonMap())) }

    override suspend fun updateRoom(id: String, roomId: String, version: Int, room: TypedValues) =
        unitCall("PATCH room") { api.updateRoom(id, roomId, JsonObject(room.toJsonMap() + ("version" to JsonPrimitive(version)))) }

    override suspend fun addBed(id: String, roomId: String, bedNumber: String, rent: String?) = unitCall("POST bed") {
        api.addBed(
            id,
            roomId,
            buildJsonObject {
                put("bedNumber", bedNumber.trim())
                rent?.trim()?.takeIf { it.isNotEmpty() }?.let { put("rent", it) }
            },
        )
    }

    override suspend fun savePricing(id: String, pricing: PricingInput, unitId: String?): AppResult<Unit> {
        val body = JsonObject(
            pricing.values.toJsonMap() + (
                "charges" to buildJsonArray {
                    pricing.charges.forEach { c ->
                        addJsonObject {
                            put("kind", c.kind)
                            put("billing", c.billing)
                            c.amount.trim().takeIf { it.isNotEmpty() }?.let { put("amount", it) }
                            c.frequency.takeIf { it.isNotBlank() }?.let { put("frequency", it) }
                        }
                    }
                }
                ),
        )
        return unitCall("PUT pricing") {
            if (unitId == null) api.savePricing(id, body) else api.saveUnitPricing(id, unitId, body)
        }
    }

    override suspend fun saveUtilities(id: String, values: TypedValues) =
        unitCall("PUT utilities") { api.saveUtilities(id, JsonObject(values.toJsonMap())) }

    override suspend fun savePolicies(id: String, values: TypedValues, rules: List<Pair<String, String>>) = unitCall("PUT policies") {
        val rulesJson = buildJsonArray {
            rules.filter { it.first.isNotBlank() }.forEach { (title, description) ->
                addJsonObject {
                    put("title", title.trim())
                    put("description", description.trim())
                }
            }
        }
        api.savePolicies(id, JsonObject(values.toJsonMap() + ("additionalRules" to rulesJson)))
    }

    override suspend fun amenities(): AppResult<List<AmenityGroup>> =
        call("GET amenities", { api.amenities() }) { it.toAmenityGroups() }

    override suspend fun saveAmenities(id: String, items: Map<String, String>, custom: List<String>, unitId: String?, roomId: String?) = unitCall("PUT amenities") {
        api.saveAmenities(
            id,
            buildJsonObject {
                unitId?.let { put("unitId", it) }
                roomId?.let { put("roomId", it) }
                putJsonArray("items") {
                    items.forEach { (key, status) ->
                        addJsonObject {
                            put("key", key)
                            put("status", status)
                        }
                    }
                }
                putJsonArray("custom") {
                    custom.filter { it.isNotBlank() }.forEach { label ->
                        addJsonObject {
                            put("label", label.trim())
                            put("status", "YES")
                        }
                    }
                }
            },
        )
    }

    override suspend fun deleteImage(id: String, imageId: String) = unitCall("DELETE image") { api.deleteImage(id, imageId) }

    override suspend fun setImageOrder(id: String, imageId: String, displayOrder: Int) = unitCall("PATCH image") {
        api.updateImage(id, imageId, buildJsonObject { put("displayOrder", displayOrder) })
    }

    override suspend fun documents(id: String): AppResult<List<PropertyDocument>> =
        call("GET documents", { api.documents(id) }) { data ->
            data.arrayAt("documents", "items").mapNotNull { e ->
                runCatching { json.decodeFromJsonElement(PropertyDocumentDto.serializer(), e).toDomain() }.getOrNull()
            }
        }

    override suspend fun uploadDocument(id: String, filePath: String, docType: String): AppResult<PropertyDocument> {
        val file = File(filePath)
        return call("POST document", { api.uploadDocument(id, jpegPart("file", file), textPart(docType)) }) { it.document?.toDomain() }
            .also { if (it is AppResult.Success) file.delete() }
    }

    override suspend fun replaceDocument(docId: String, filePath: String): AppResult<PropertyDocument> {
        val file = File(filePath)
        return call("POST document replace", { api.replaceDocument(docId, jpegPart("file", file)) }) { it.document?.toDomain() }
            .also { if (it is AppResult.Success) file.delete() }
    }

    override suspend fun completeness(id: String): AppResult<Completeness> =
        call("GET completeness (draft view)", { api.completeness(id, DRAFT_VIEW) }) { it.toDomain() }

    override suspend fun submit(id: String): AppResult<Property> =
        call("POST submit", { api.submit(id) }) { it.property?.toDomain() }

    // ---------- helpers ----------

    /** Success with data → [map]; null mapping = unexpected response. Errors → property errors. */
    private suspend fun <T, R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<T>>,
        map: (T) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> r.data?.let(map)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        is ApiResult.Failure -> AppResult.Failure(r.error.toPropertyError(json))
    }

    /** Calls where only success matters (the wizard reloads the property afterwards). */
    private suspend fun unitCall(name: String, request: suspend () -> Response<ApiEnvelope<JsonElement>>): AppResult<Unit> =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success, is ApiResult.NotModified -> AppResult.Success(Unit)
            is ApiResult.Failure -> AppResult.Failure(r.error.toPropertyError(json))
        }

    private fun round6(v: Double): Double = String.format(Locale.US, "%.6f", v).toDouble()

    companion object {
        const val DRAFT_VIEW = "draft"
        private val JPEG = "image/jpeg".toMediaType()
        private val TEXT = "text/plain".toMediaType()

        fun jpegPart(field: String, file: File): MultipartBody.Part =
            MultipartBody.Part.createFormData(field, file.name, file.asRequestBody(JPEG))

        fun textPart(value: String): RequestBody = value.toRequestBody(TEXT)
    }
}
