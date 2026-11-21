package com.saferenttechnologies.android.data.inspection

import android.content.Context
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.asText
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.owner.OwnerMapper
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.InspectionApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Condition
import com.saferenttechnologies.android.domain.model.Inspection
import com.saferenttechnologies.android.domain.model.InspectionForm
import com.saferenttechnologies.android.domain.model.InspectionPhoto
import com.saferenttechnologies.android.domain.model.InspectionRules
import com.saferenttechnologies.android.domain.model.InventoryItem
import com.saferenttechnologies.android.domain.model.RepairItem
import com.saferenttechnologies.android.domain.model.ScheduleForm
import com.saferenttechnologies.android.domain.repository.InspectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InspectionRepositoryImpl @Inject constructor(
    private val api: InspectionApi,
    private val safeApiCall: SafeApiCall,
    @ApplicationContext private val context: Context,
) : InspectionRepository {

    override suspend fun list(propertyId: String): AppResult<List<Inspection>> =
        call("GET inspections", { api.list(propertyId, 1, PAGE_SIZE) }) { d, _ -> d.arrayAt("items", "inspections").mapNotNull { (it as? JsonObject)?.let(InspectionMapper::inspection) } }

    override suspend fun schedule(propertyId: String, form: ScheduleForm): AppResult<Inspection> =
        call("POST inspection", { api.create(propertyId, InspectionMapper.scheduleBody(form)) }) { d, _ ->
            ((d as? JsonObject)?.get("inspection") as? JsonObject)?.let(InspectionMapper::inspection)
        }

    override suspend fun record(propertyId: String, inspectionId: String, form: InspectionForm): AppResult<String> =
        call("PATCH inspection", { api.update(propertyId, inspectionId, InspectionMapper.body(form)) }) { _, message -> message }

    override suspend fun acknowledge(propertyId: String, inspectionId: String): AppResult<String> =
        call("POST inspection acknowledge", { api.acknowledge(propertyId, inspectionId) }) { _, message -> message }

    override suspend fun uploadPhoto(propertyId: String, inspectionId: String, category: String, filePath: String): AppResult<Unit> {
        val file = File(filePath)
        if (!file.exists()) return AppResult.Failure(AppError.Unknown("Photo is missing. Take it again."))
        val text = "text/plain".toMediaType()
        val part = MultipartBody.Part.createFormData("files", file.name, file.asRequestBody("image/jpeg".toMediaType()))
        val r: AppResult<Unit> = call("POST inspection photo", { api.uploadPhotos(propertyId, listOf(part), inspectionId.toRequestBody(text), category.toRequestBody(text)) }) { _, _ -> Unit }
        if (r is AppResult.Success) file.delete()
        return r
    }

    // ---- local draft: one small JSON file per inspection (app private storage) ----

    private fun draftFile(id: String) = File(File(context.filesDir, "inspection_drafts").apply { mkdirs() }, "${id.filter { it.isLetterOrDigit() || it == '-' }}.json")

    override suspend fun draft(inspectionId: String): InspectionForm? = withContext(Dispatchers.IO) {
        runCatching {
            val f = draftFile(inspectionId)
            if (!f.exists()) return@runCatching null
            (SafeRentJson.instance.parseToJsonElement(f.readText()) as? JsonObject)?.let(InspectionMapper::form)
        }.getOrNull()
    }

    override suspend fun saveDraft(inspectionId: String, form: InspectionForm?) {
        withContext(Dispatchers.IO) {
            runCatching {
                val f = draftFile(inspectionId)
                if (form == null) f.delete() else f.writeText(InspectionMapper.draftJson(form).toString())
            }
        }
    }

    private suspend fun <R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<JsonElement>>,
        map: (data: JsonElement, message: String) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> {
            if (!name.startsWith("GET")) ApiCache.clear()
            map(r.data ?: JsonNull, r.message)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        }
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        // 400 → field issues with full paths, 412 → InvalidState …
        is ApiResult.Failure -> AppResult.Failure(OwnerMapper.error(r.error))
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}

/** JSON ⇄ inspection models (pure, unit-tested). */
object InspectionMapper {
    private fun JsonObject.number(k: String): String =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty()

    private fun inventory(e: JsonElement?): List<InventoryItem> = (e as? JsonArray)?.mapNotNull { x ->
        val o = x as? JsonObject ?: return@mapNotNull null
        InventoryItem(
            o.text("item"), o.number("quantity").ifBlank { "1" }, Condition.of(o.text("condition")) ?: Condition.GOOD,
            inventoryItemId = o.text("inventoryItemId").ifBlank { null },
            workingStatus = com.saferenttechnologies.android.domain.model.WorkingStatus.of(o.text("workingStatus")),
        )
    }.orEmpty()

    private fun repairs(e: JsonElement?): List<RepairItem> = (e as? JsonArray)?.mapNotNull { x ->
        val o = x as? JsonObject ?: return@mapNotNull null
        RepairItem(
            item = o.text("item"),
            description = o.text("description"),
            priority = o.text("priority").uppercase().ifBlank { "MEDIUM" },
            estimatedCost = o.number("estimatedCost"),
            responsibility = o.text("responsibility").uppercase().ifBlank { "OWNER" },
            status = o.text("status").uppercase().ifBlank { "OPEN" },
        )
    }.orEmpty()

    fun inspection(o: JsonObject): Inspection? {
        val id = o.text("id").ifBlank { return null }
        return Inspection(
            id = id,
            propertyId = o.text("propertyId"),
            type = o.text("type").uppercase().ifBlank { "ROUTINE" },
            unitId = o.text("unitId").ifBlank { null },
            roomId = o.text("roomId").ifBlank { null },
            bedId = o.text("bedId").ifBlank { null },
            leaseId = o.text("leaseId").ifBlank { null },
            scheduledAt = o.text("scheduledAt"),
            inspectionDate = o.text("inspectionDate"),
            status = o.text("status").uppercase().ifBlank { "SCHEDULED" },
            conditions = InspectionRules.CONDITION_KEYS.mapNotNull { k -> Condition.of(o.text(k))?.let { k to it } }.toMap(),
            furniture = inventory(o["furnitureInventory"]),
            appliances = inventory(o["applianceInventory"]),
            repairs = repairs(o["repairRequirements"]),
            existingDamages = o.text("existingDamages"),
            meterReading = o.number("handoverMeterReading"),
            keysHandedOver = o.number("keysHandedOver"),
            result = o.text("result").uppercase(),
            notes = o.text("notes"),
            landlordAcknowledgedAt = o.text("landlordAcknowledgedAt"),
            tenantAcknowledgedAt = o.text("tenantAcknowledgedAt"),
            photos = (o["photos"] as? JsonArray)?.mapNotNull { p ->
                val x = p as? JsonObject ?: return@mapNotNull null
                val pid = x.text("id").ifBlank { return@mapNotNull null }
                InspectionPhoto(pid, x.text("category").uppercase().ifBlank { "OTHER" }, x.text("url").ifBlank { "/api/v1/files/property-images/$pid" })
            }.orEmpty(),
        )
    }

    fun scheduleBody(f: ScheduleForm): JsonObject = buildJsonObject {
        put("type", f.type.name)
        put("scheduledAt", f.scheduledAt)
        f.unitId?.let { put("unitId", it) }
        f.roomId?.let { put("roomId", it) }
        f.bedId?.let { put("bedId", it) }
        f.leaseId?.let { put("leaseId", it) }
        f.notes.trim().takeIf { it.isNotEmpty() }?.let { put("notes", it) }
    }

    private fun BigDecimal.toNumber(): Number = stripTrailingZeros().let { if (it.scale() <= 0) it.toLong() else it.toDouble() }

    private fun inventoryJson(rows: List<InventoryItem>) = buildJsonArray {
        rows.forEach { r ->
            add(buildJsonObject {
                put("item", r.item)
                put("quantity", r.quantity.toIntOrNull() ?: 0)
                put("condition", r.condition.name)
                // Linked to the furniture register (completed → the server updates that item).
                r.inventoryItemId?.let { put("inventoryItemId", it) }
                r.workingStatus?.let { put("workingStatus", it.name) }
            })
        }
    }

    /** PATCH body from the cleaned form: empty texts → null, empty lists → []. */
    fun body(raw: InspectionForm): JsonObject {
        val f = InspectionRules.cleaned(raw)
        return buildJsonObject {
            if (f.complete) put("status", "COMPLETED")
            if (f.inspectionDate.isNotBlank()) put("inspectionDate", f.inspectionDate)
            f.conditions.forEach { (k, v) -> put(k, v.name) }
            put("furnitureInventory", inventoryJson(f.furniture))
            put("applianceInventory", inventoryJson(f.appliances))
            put(
                "repairRequirements",
                buildJsonArray {
                    f.repairs.forEach { r ->
                        add(buildJsonObject {
                            put("item", r.item)
                            put("description", r.description.ifEmpty { null })
                            put("priority", r.priority)
                            put("estimatedCost", r.estimatedCost.toBigDecimalOrNull()?.toNumber())
                            put("responsibility", r.responsibility)
                            put("status", r.status)
                        })
                    }
                },
            )
            put("existingDamages", f.existingDamages.ifEmpty { null })
            put("handoverMeterReading", f.meterReading.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString())
            put("keysHandedOver", f.keysHandedOver.toIntOrNull())
            put("result", f.result.ifEmpty { null })
            put("notes", f.notes.ifEmpty { null })
        }
    }

    /** Draft on the phone: the raw form, rows as typed (not cleaned). */
    fun draftJson(f: InspectionForm): JsonObject = buildJsonObject {
        put("complete", f.complete)
        put("inspectionDate", f.inspectionDate)
        f.conditions.forEach { (k, v) -> put(k, v.name) }
        put("furnitureInventory", buildJsonArray { f.furniture.forEach { r -> add(draftRow(r)) } })
        put("applianceInventory", buildJsonArray { f.appliances.forEach { r -> add(draftRow(r)) } })
        put(
            "repairRequirements",
            buildJsonArray {
                f.repairs.forEach { r ->
                    add(buildJsonObject {
                        put("item", r.item); put("description", r.description); put("priority", r.priority)
                        put("estimatedCost", r.estimatedCost); put("responsibility", r.responsibility); put("status", r.status)
                    })
                }
            },
        )
        put("existingDamages", f.existingDamages)
        put("handoverMeterReading", f.meterReading)
        put("keysHandedOver", f.keysHandedOver)
        put("result", f.result)
        put("notes", f.notes)
    }

    private fun draftRow(r: InventoryItem) = buildJsonObject {
        put("item", r.item); put("quantity", r.quantity); put("condition", r.condition.name)
        r.inventoryItemId?.let { put("inventoryItemId", it) }
        r.workingStatus?.let { put("workingStatus", it.name) }
    }

    private fun draftItem(o: JsonObject) = InventoryItem(
        o.text("item"), o.text("quantity"), Condition.of(o.text("condition")) ?: Condition.GOOD,
        inventoryItemId = o.text("inventoryItemId").ifBlank { null },
        workingStatus = com.saferenttechnologies.android.domain.model.WorkingStatus.of(o.text("workingStatus")),
    )

    fun form(o: JsonObject): InspectionForm {
        val i = inspection(buildJsonObject { o.forEach { (k, v) -> put(k, v) }; put("id", "draft") })
        val base = i?.let(InspectionForm::from) ?: InspectionForm()
        val complete = (o["complete"] as? JsonPrimitive)?.content?.equals("true", true) ?: true
        // Drafts keep quantities / costs exactly as typed.
        return base.copy(
            complete = complete,
            meterReading = o.text("handoverMeterReading"),
            keysHandedOver = o.text("keysHandedOver"),
            furniture = (o["furnitureInventory"] as? JsonArray)?.mapNotNull { x -> (x as? JsonObject)?.let(::draftItem) }.orEmpty(),
            appliances = (o["applianceInventory"] as? JsonArray)?.mapNotNull { x -> (x as? JsonObject)?.let(::draftItem) }.orEmpty(),
            repairs = (o["repairRequirements"] as? JsonArray)?.mapNotNull { x ->
                (x as? JsonObject)?.let { RepairItem(it.text("item"), it.text("description"), it.text("priority").ifBlank { "MEDIUM" }, it.text("estimatedCost"), it.text("responsibility").ifBlank { "OWNER" }, it.text("status").ifBlank { "OPEN" }) }
            }.orEmpty(),
        )
    }
}
