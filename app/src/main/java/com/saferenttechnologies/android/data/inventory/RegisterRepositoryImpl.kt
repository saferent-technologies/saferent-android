package com.saferenttechnologies.android.data.inventory

import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.owner.OwnerMapper
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.RegisterApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Condition
import com.saferenttechnologies.android.domain.model.Register
import com.saferenttechnologies.android.domain.model.RegisterItem
import com.saferenttechnologies.android.domain.model.RegisterItemForm
import com.saferenttechnologies.android.domain.model.RegisterPhoto
import com.saferenttechnologies.android.domain.model.RegisterRepair
import com.saferenttechnologies.android.domain.model.RegisterRules
import com.saferenttechnologies.android.domain.model.RegisterSummary
import com.saferenttechnologies.android.domain.model.RepairForm
import com.saferenttechnologies.android.domain.model.WorkingStatus
import com.saferenttechnologies.android.domain.repository.RegisterRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegisterRepositoryImpl @Inject constructor(
    private val api: RegisterApi,
    private val safeApiCall: SafeApiCall,
) : RegisterRepository {

    override suspend fun register(propertyId: String, draft: Boolean, unitId: String?, roomId: String?): AppResult<Register> {
        val query = buildMap {
            if (draft) put("view", "draft")
            unitId?.let { put("unitId", it) }
            roomId?.let { put("roomId", it) }
        }
        return call("GET inventory", { api.list(propertyId, query) }) { d, _ -> RegisterMapper.register(d) }
    }

    override suspend fun create(propertyId: String, form: RegisterItemForm, draft: Boolean): AppResult<Pair<String, RegisterItem?>> =
        call("POST inventory item", { api.create(propertyId, RegisterMapper.body(RegisterRules.fields(form))) }) { d, m -> m to RegisterMapper.itemOf(d) }

    override suspend fun update(propertyId: String, item: RegisterItem, form: RegisterItemForm, draft: Boolean): AppResult<Pair<String, RegisterItem?>> {
        val changes = RegisterRules.fields(form, item)
        if (changes.isEmpty()) return AppResult.Success("" to item)
        return call("PATCH inventory item", { api.update(propertyId, item.id, RegisterMapper.body(changes)) }) { d, m -> m to RegisterMapper.itemOf(d) }
    }

    override suspend fun delete(propertyId: String, itemId: String): AppResult<String> =
        call("DELETE inventory item", { api.delete(propertyId, itemId) }) { _, m -> m }

    override suspend fun uploadPhoto(propertyId: String, itemId: String, filePath: String): AppResult<String> {
        val file = File(filePath)
        if (!file.exists()) return AppResult.Failure(AppError.Unknown("Photo is missing. Take it again."))
        val part = MultipartBody.Part.createFormData("files", file.name, file.asRequestBody("image/jpeg".toMediaType()))
        val r = call("POST inventory photo", { api.uploadPhotos(propertyId, itemId, listOf(part)) }) { _, m -> m }
        if (r is AppResult.Success) file.delete()
        return r
    }

    override suspend fun deletePhoto(propertyId: String, itemId: String, photoId: String): AppResult<Unit> =
        call("DELETE inventory photo", { api.deletePhoto(propertyId, itemId, photoId) }) { _, _ -> Unit }

    override suspend fun addRepair(propertyId: String, itemId: String, form: RepairForm): AppResult<RegisterItem?> =
        call("POST inventory repair", { api.addRepair(propertyId, itemId, RegisterMapper.repairBody(form)) }) { d, _ -> RegisterMapper.itemOf(d) ?: NO_ITEM }
            .let { r -> if (r is AppResult.Success && r.data === NO_ITEM) AppResult.Success(null) else r }

    override suspend fun markRepairDone(propertyId: String, itemId: String, repairId: String): AppResult<RegisterItem?> =
        call("PATCH inventory repair", { api.updateRepair(propertyId, itemId, repairId, buildJsonObject { put("status", "DONE") }) }) { d, _ -> RegisterMapper.itemOf(d) ?: NO_ITEM }
            .let { r -> if (r is AppResult.Success && r.data === NO_ITEM) AppResult.Success(null) else r }

    override suspend fun deleteRepair(propertyId: String, itemId: String, repairId: String): AppResult<Unit> =
        call("DELETE inventory repair", { api.deleteRepair(propertyId, itemId, repairId) }) { _, _ -> Unit }

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
        is ApiResult.Failure -> AppResult.Failure(OwnerMapper.error(r.error))
    }

    private companion object {
        /** Placeholder so a success without an item isn't a parse error. */
        val NO_ITEM = RegisterItem("")
    }
}

/** JSON ⇄ register models (pure, unit-tested). Also reads the tenant's reduced items and listing "furnishings". */
object RegisterMapper {
    private fun JsonObject.number(k: String): String =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty()

    fun item(o: JsonObject): RegisterItem? {
        val id = o.text("id").ifBlank { return null }
        return RegisterItem(
            id = id,
            unitId = o.text("unitId").ifBlank { null },
            roomId = o.text("roomId").ifBlank { null },
            name = o.text("name"),
            category = o.text("category").uppercase().takeIf { it in RegisterRules.CATEGORIES } ?: "OTHER",
            quantity = (o["quantity"] as? JsonPrimitive)?.intOrNull ?: o.number("quantity").toBigDecimalOrNull()?.toInt() ?: 1,
            brand = o.text("brand"),
            model = o.text("model"),
            purchaseDate = o.text("purchaseDate").take(10),
            condition = Condition.of(o.text("condition")) ?: Condition.GOOD,
            workingStatus = WorkingStatus.of(o.text("workingStatus")) ?: WorkingStatus.WORKING,
            serialNumber = o.text("serialNumber"),
            notes = o.text("notes"),
            displayOrder = (o["displayOrder"] as? JsonPrimitive)?.intOrNull ?: 0,
            photos = (o["photos"] as? JsonArray)?.mapNotNull { p ->
                val x = p as? JsonObject ?: return@mapNotNull null
                val pid = x.text("id").ifBlank { return@mapNotNull null }
                RegisterPhoto(pid, x.text("url").ifBlank { "/api/v1/files/inventory-photos/$pid" })
            }.orEmpty(),
            repairs = (o["repairs"] as? JsonArray)?.mapNotNull { r ->
                val x = r as? JsonObject ?: return@mapNotNull null
                RegisterRepair(
                    id = x.text("id").ifBlank { return@mapNotNull null },
                    repairDate = x.text("repairDate").take(10),
                    description = x.text("description"),
                    cost = x.number("cost"),
                    paidBy = x.text("paidBy").uppercase().ifBlank { "OWNER" },
                    doneBy = x.text("doneBy"),
                    status = x.text("status").uppercase().ifBlank { "DONE" },
                    createdAt = x.text("createdAt"),
                )
            }?.sortedWith(compareByDescending<RegisterRepair> { it.repairDate }.thenByDescending { it.createdAt }).orEmpty(),
            openRepairs = (o["openRepairs"] as? JsonPrimitive)?.intOrNull ?: 0,
        )
    }

    fun items(data: JsonElement): List<RegisterItem> = data.arrayAt("items", "furnishings").mapNotNull { (it as? JsonObject)?.let(::item) }

    fun register(data: JsonElement): Register {
        val o = data as? JsonObject
        val s = o?.get("summary") as? JsonObject
        return Register(
            items = items(data),
            summary = RegisterSummary(
                total = (s?.get("total") as? JsonPrimitive)?.intOrNull ?: 0,
                needsRepair = (s?.get("needsRepair") as? JsonPrimitive)?.intOrNull ?: 0,
                openRepairs = (s?.get("openRepairs") as? JsonPrimitive)?.intOrNull ?: 0,
            ),
            suggestions = (o?.get("suggestions") as? JsonObject)?.mapValues { (_, v) -> (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty() }.orEmpty(),
        )
    }

    fun itemOf(data: JsonElement): RegisterItem? = ((data as? JsonObject)?.get("item") as? JsonObject)?.let(::item)

    /** String → string, Int → number, null → JSON null. */
    fun body(fields: Map<String, Any?>): JsonObject = buildJsonObject {
        fields.forEach { (k, v) ->
            when (v) {
                null -> put(k, JsonNull)
                is Int -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }

    fun repairBody(f: RepairForm): JsonObject = buildJsonObject {
        put("repairDate", f.repairDate.trim())
        put("description", f.description.trim())
        put("cost", f.cost.trim().toBigDecimalOrNull()?.let { it.stripTrailingZeros().let { c -> if (c.scale() <= 0) c.toLong() else c.toDouble() } })
        put("paidBy", f.paidBy)
        put("doneBy", f.doneBy.trim().ifEmpty { null })
        put("status", f.status)
    }
}
