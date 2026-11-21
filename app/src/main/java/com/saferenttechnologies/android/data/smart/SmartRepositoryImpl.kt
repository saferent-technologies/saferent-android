package com.saferenttechnologies.android.data.smart

import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.owner.OwnerMapper
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SmartApi
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.AreaChange
import com.saferenttechnologies.android.domain.model.ComparisonRepair
import com.saferenttechnologies.android.domain.model.ComparisonSummary
import com.saferenttechnologies.android.domain.model.InspectionComparison
import com.saferenttechnologies.android.domain.model.InspectionDiff
import com.saferenttechnologies.android.domain.model.InspectionPhoto
import com.saferenttechnologies.android.domain.model.InspectionSide
import com.saferenttechnologies.android.domain.model.ItemChange
import com.saferenttechnologies.android.domain.model.ItemSnapshot
import com.saferenttechnologies.android.domain.model.PropertyQr
import com.saferenttechnologies.android.domain.model.QrResolution
import com.saferenttechnologies.android.domain.model.QrTarget
import com.saferenttechnologies.android.domain.model.ReminderData
import com.saferenttechnologies.android.domain.model.ReminderRules
import com.saferenttechnologies.android.domain.model.ReminderSettings
import com.saferenttechnologies.android.domain.repository.SmartRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartRepositoryImpl @Inject constructor(
    private val api: SmartApi,
    private val safeApiCall: SafeApiCall,
) : SmartRepository {

    override suspend fun comparison(leaseId: String) = call("GET inspection comparison", { api.comparison(leaseId) }) { d, _ -> SmartMapper.comparison(d) }

    override suspend fun propertyQr(propertyId: String) = call("GET property qr", { api.propertyQr(propertyId) }) { d, _ -> SmartMapper.propertyQr(d) }

    // Codes stay out of logs (only the call name is logged).
    override suspend fun resolveQr(code: String, unit: String?) = call("GET qr resolve", { api.resolve(code, unit) }) { d, _ -> SmartMapper.resolution(d) }

    override suspend fun reminders() = call("GET reminder settings", { api.reminders() }) { d, _ -> SmartMapper.reminders(d) }

    override suspend fun saveReminders(settings: ReminderSettings) =
        call("PUT reminder settings", { api.saveReminders(SmartMapper.reminderBody(settings)) }) { d, m -> m to SmartMapper.reminders(d) }

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
}

/** JSON → smart-feature models (pure, unit-tested). */
object SmartMapper {
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } == true
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonObject.textOrNull(k: String) = text(k).trim().ifBlank { null }
    private fun JsonElement.ints() = arrayAt().mapNotNull { (it as? JsonPrimitive)?.let { p -> p.intOrNull ?: p.doubleOrNull?.toInt() } }

    private fun side(o: JsonObject?): InspectionSide? = o?.let {
        val id = it.text("id").ifBlank { return null }
        InspectionSide(
            id = id,
            status = it.text("status").uppercase(),
            inspectionDate = it.text("inspectionDate"),
            landlordAcknowledgedAt = it.textOrNull("landlordAcknowledgedAt"),
            tenantAcknowledgedAt = it.textOrNull("tenantAcknowledgedAt"),
            photos = it.arrayAt("photos").mapNotNull { e ->
                val p = e as? JsonObject ?: return@mapNotNull null
                val pid = p.text("id").ifBlank { return@mapNotNull null }
                InspectionPhoto(pid, p.text("category").uppercase().ifBlank { "OTHER" }, p.text("url").ifBlank { "/api/v1/files/property-images/$pid" })
            },
        )
    }

    private fun snapshot(o: JsonObject?) = o?.let { ItemSnapshot(it.int("quantity") ?: 0, it.textOrNull("condition")?.uppercase()) }

    private fun items(o: JsonObject, key: String) = o.arrayAt(key).mapNotNull { e ->
        val x = e as? JsonObject ?: return@mapNotNull null
        ItemChange(x.text("item"), snapshot(x.obj("before")), snapshot(x.obj("after")), x.text("change").uppercase().ifBlank { "NOT_COMPARABLE" }, x.int("missing") ?: 0)
    }

    fun comparison(d: JsonElement): InspectionComparison {
        val o = d as? JsonObject ?: return InspectionComparison()
        val c = o.obj("comparison")
        return InspectionComparison(
            ready = o.bool("ready"),
            missing = o.arrayAt("missing").mapNotNull { (it as? JsonPrimitive)?.content?.uppercase() },
            moveIn = side(o.obj("moveIn")),
            moveOut = side(o.obj("moveOut")),
            comparison = c?.let { x ->
                val meter = x.obj("meter")
                val keys = x.obj("keys")
                val damages = x.obj("damages")
                val sum = x.obj("summary")
                InspectionDiff(
                    areas = x.arrayAt("areas").mapNotNull { e ->
                        (e as? JsonObject)?.let { a -> AreaChange(a.text("key"), a.text("label"), a.textOrNull("before")?.uppercase(), a.textOrNull("after")?.uppercase(), a.text("change").uppercase().ifBlank { "NOT_COMPARABLE" }) }
                    },
                    furniture = items(x, "furniture"),
                    appliances = items(x, "appliances"),
                    meterIn = meter?.num("moveIn"),
                    meterOut = meter?.num("moveOut"),
                    meterUsed = meter?.num("used"),
                    keysIn = keys?.int("moveIn"),
                    keysOut = keys?.int("moveOut"),
                    keysMissing = keys?.int("missing"),
                    damagesIn = damages?.textOrNull("moveIn"),
                    damagesOut = damages?.textOrNull("moveOut"),
                    repairs = x.arrayAt("repairsAtMoveOut").mapNotNull { e ->
                        (e as? JsonObject)?.let { r ->
                            ComparisonRepair(r.text("item"), r.text("description"), r.text("priority").uppercase(), r.num("estimatedCost"), r.text("responsibility").uppercase(), r.text("status").uppercase())
                        }
                    },
                    summary = ComparisonSummary(sum?.int("areasWorse") ?: 0, sum?.int("itemsMissing") ?: 0, sum?.int("itemsWorse") ?: 0, sum?.bool("hasIssues") == true),
                )
            },
        )
    }

    fun propertyQr(d: JsonElement): PropertyQr? {
        val o = d as? JsonObject ?: return null
        val p = o.obj("property") ?: return null
        val url = p.text("url").ifBlank { return null }
        return PropertyQr(
            property = QrTarget(p.text("code"), p.text("title"), url),
            units = o.arrayAt("units").mapNotNull { e ->
                val u = e as? JsonObject ?: return@mapNotNull null
                val uu = u.text("url").ifBlank { return@mapNotNull null }
                QrTarget(u.text("code"), u.text("label").ifBlank { u.text("code") }, uu, u.textOrNull("unitId"))
            },
        )
    }

    fun resolution(d: JsonElement): QrResolution {
        val o = d as? JsonObject ?: return QrResolution()
        val p = o.obj("property")
        val u = o.obj("unit")
        return QrResolution(
            role = o.text("role").uppercase().ifBlank { "NONE" },
            propertyId = p?.textOrNull("id"),
            propertyCode = p?.text("code").orEmpty(),
            propertyTitle = p?.text("title").orEmpty(),
            unitId = u?.textOrNull("id"),
            unitCode = u?.textOrNull("code"),
            unitLabel = u?.textOrNull("label"),
            leaseId = o.textOrNull("leaseId"),
            actions = o.arrayAt("actions").mapNotNull { (it as? JsonPrimitive)?.content?.uppercase() },
            message = o.textOrNull("message"),
        )
    }

    private fun settings(o: JsonObject?, fallback: ReminderSettings) = o?.let {
        ReminderSettings(
            enabled = (it["enabled"] as? JsonPrimitive)?.booleanOrNull ?: fallback.enabled,
            beforeDays = it["beforeDays"]?.ints() ?: fallback.beforeDays,
            overdueDays = it["overdueDays"]?.ints() ?: fallback.overdueDays,
            smartEarly = (it["smartEarly"] as? JsonPrimitive)?.booleanOrNull ?: fallback.smartEarly,
        )
    } ?: fallback

    fun reminders(d: JsonElement): ReminderData {
        val o = d as? JsonObject ?: return ReminderData()
        val defaults = settings(o.obj("defaults"), ReminderSettings())
        return ReminderData(ReminderRules.clean(settings(o.obj("settings"), defaults)), defaults, o.text("sendingHours"))
    }

    fun reminderBody(raw: ReminderSettings): JsonObject {
        val s = ReminderRules.clean(raw)
        return buildJsonObject {
            put("enabled", s.enabled)
            put("beforeDays", buildJsonArray { s.beforeDays.forEach { add(JsonPrimitive(it)) } })
            put("overdueDays", buildJsonArray { s.overdueDays.forEach { add(JsonPrimitive(it)) } })
            put("smartEarly", s.smartEarly)
        }
    }
}
