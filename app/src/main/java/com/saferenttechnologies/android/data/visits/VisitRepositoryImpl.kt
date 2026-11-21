package com.saferenttechnologies.android.data.visits

import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.owner.OwnerMapper
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.VisitApi
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.BookVisit
import com.saferenttechnologies.android.domain.model.SlotDay
import com.saferenttechnologies.android.domain.model.UpcomingVisit
import com.saferenttechnologies.android.domain.model.Visit
import com.saferenttechnologies.android.domain.model.VisitFeedback
import com.saferenttechnologies.android.domain.model.VisitHost
import com.saferenttechnologies.android.domain.model.VisitSettings
import com.saferenttechnologies.android.domain.model.VisitSettingsData
import com.saferenttechnologies.android.domain.model.VisitSlot
import com.saferenttechnologies.android.domain.model.VisitSlots
import com.saferenttechnologies.android.domain.model.WeeklyWindow
import com.saferenttechnologies.android.domain.repository.VisitRepository
import kotlinx.serialization.json.JsonArray
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
class VisitRepositoryImpl @Inject constructor(
    private val api: VisitApi,
    private val safeApiCall: SafeApiCall,
) : VisitRepository {

    override suspend fun settings(propertyId: String): AppResult<VisitSettingsData> =
        call("GET visit settings", { api.settings(propertyId) }) { d, _ -> VisitMapper.settingsData(d) }

    override suspend fun saveSettings(propertyId: String, settings: VisitSettings): AppResult<Pair<String, VisitSettingsData>> =
        call("PUT visit settings", { api.saveSettings(propertyId, VisitMapper.settingsBody(settings)) }) { d, m -> m to VisitMapper.settingsData(d) }

    override suspend fun slots(propertyId: String, from: String?, days: Int): AppResult<VisitSlots> {
        val q = buildMap { from?.let { put("from", it) }; put("days", days.coerceIn(1, 31).toString()) }
        return call("GET visit slots", { api.slots(propertyId, q) }) { d, _ -> VisitMapper.slots(d) }
    }

    override suspend fun book(propertyId: String, booking: BookVisit): AppResult<Pair<String, Visit?>> {
        val body = buildJsonObject {
            put("start", booking.start)
            booking.unitId?.let { put("unitId", it) }
            booking.bedId?.let { put("bedId", it) }
            put("visitors", booking.visitors.coerceIn(1, 10))
            booking.note.trim().takeIf { it.isNotEmpty() }?.let { put("note", it.take(300)) }
        }
        return call("POST visit", { api.book(propertyId, body) }) { d, m -> m to VisitMapper.visitOf(d) }
    }

    override suspend fun myVisits(upcoming: Boolean): AppResult<List<Visit>> {
        val q = buildMap { if (upcoming) put("upcoming", "true"); put("page", "1"); put("pageSize", "50") }
        return call("GET my visits", { api.myVisits(q) }) { d, _ -> d.arrayAt("items").mapNotNull { (it as? JsonObject)?.let(VisitMapper::visit) } }
    }

    override suspend fun cancelMine(visitId: String, reason: String) =
        call("POST my visit cancel", { api.cancelMine(visitId, buildJsonObject { put("reason", reason.trim()) }) }) { d, m -> m to VisitMapper.visitOf(d) }

    override suspend fun feedback(visitId: String, rating: Int, interested: Boolean, comment: String) =
        call("POST visit feedback", {
            api.feedback(visitId, buildJsonObject { put("rating", rating.coerceIn(1, 5)); put("interested", interested); comment.trim().takeIf { it.isNotEmpty() }?.let { put("comment", it.take(500)) } })
        }) { d, m -> m to VisitMapper.visitOf(d) }

    override suspend fun hostVisits(propertyId: String?): AppResult<List<Visit>> {
        val q = buildMap { propertyId?.let { put("propertyId", it) }; put("page", "1"); put("pageSize", "50") }
        return call("GET host visits", { api.hostVisits(q) }) { d, _ -> d.arrayAt("items").mapNotNull { (it as? JsonObject)?.let(VisitMapper::visit) } }
    }

    override suspend fun hostAction(visitId: String, action: String, reason: String?, outcome: String?) =
        call("POST visit $action", {
            api.hostAction(visitId, action, buildJsonObject {
                reason?.trim()?.takeIf { it.isNotEmpty() }?.let { put("reason", it) }
                outcome?.trim()?.takeIf { it.isNotEmpty() }?.let { put("outcome", it.take(300)) }
            })
        }) { d, m -> m to VisitMapper.visitOf(d) }

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

/** JSON ⇄ visit models (pure, unit-tested). */
object VisitMapper {
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.int(k: String, d: Int) = (this[k] as? JsonPrimitive)?.intOrNull ?: d
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull == true

    fun settings(o: JsonObject?): VisitSettings {
        if (o == null) return VisitSettings()
        return VisitSettings(
            enabled = o.bool("enabled"),
            approvalMode = o.text("approvalMode").uppercase().ifBlank { "MANUAL" },
            slotMinutes = o.int("slotMinutes", 30),
            capacityPerSlot = o.int("capacityPerSlot", 1),
            minNoticeHours = o.int("minNoticeHours", 4),
            maxDaysAhead = o.int("maxDaysAhead", 14),
            weekly = (o["weekly"] as? JsonArray)?.mapNotNull { e -> (e as? JsonObject)?.let { WeeklyWindow(it.int("day", 0), it.text("start"), it.text("end")) } }.orEmpty(),
            blockedDates = (o["blockedDates"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.take(10) }.orEmpty(),
            hostId = o.text("hostId").ifBlank { null },
            instructions = o.text("instructions"),
        )
    }

    fun settingsData(d: JsonElement): VisitSettingsData {
        val o = d as? JsonObject
        return VisitSettingsData(
            settings = settings(o?.obj("settings")),
            hosts = (o?.get("hosts") as? JsonArray)?.mapNotNull { e -> (e as? JsonObject)?.let { VisitHost(it.text("id"), it.text("fullName"), it.text("role").uppercase()) } }.orEmpty(),
        )
    }

    /** PUT body: the whole object. */
    fun settingsBody(s: VisitSettings): JsonObject = buildJsonObject {
        put("enabled", s.enabled)
        put("approvalMode", s.approvalMode)
        put("slotMinutes", s.slotMinutes)
        put("capacityPerSlot", s.capacityPerSlot)
        put("minNoticeHours", s.minNoticeHours)
        put("maxDaysAhead", s.maxDaysAhead)
        put("weekly", buildJsonArray { s.weekly.forEach { w -> add(buildJsonObject { put("day", w.day); put("start", w.start); put("end", w.end) }) } })
        put("blockedDates", buildJsonArray { s.blockedDates.distinct().sorted().forEach { add(JsonPrimitive(it)) } })
        put("hostId", s.hostId)
        put("instructions", s.instructions.trim().ifEmpty { null })
    }

    fun slots(d: JsonElement): VisitSlots {
        val o = d as? JsonObject ?: return VisitSlots()
        return VisitSlots(
            enabled = o.bool("enabled"),
            approvalMode = o.text("approvalMode").uppercase().ifBlank { "MANUAL" },
            slotMinutes = o.int("slotMinutes", 30),
            maxDaysAhead = o.int("maxDaysAhead", 14),
            days = (o["days"] as? JsonArray)?.mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                SlotDay(
                    x.text("date").take(10), x.int("weekday", 0),
                    (x["slots"] as? JsonArray)?.mapNotNull { s -> (s as? JsonObject)?.let { VisitSlot(it.text("start"), it.text("end"), it.int("remaining", 1)) } }.orEmpty(),
                )
            }.orEmpty(),
            myUpcomingVisit = o.obj("myUpcomingVisit")?.let { UpcomingVisit(it.text("id"), it.text("scheduledAt"), it.text("status").uppercase()) },
        )
    }

    fun visitOf(d: JsonElement): Visit? = ((d as? JsonObject)?.get("visit") as? JsonObject)?.let(::visit)

    fun visit(o: JsonObject): Visit? {
        val id = o.text("id").ifBlank { return null }
        val p = o.obj("property")
        val host = o.obj("host")
        val visitor = o.obj("visitor")
        val loc = o.obj("location")
        val fb = o.obj("feedback")
        return Visit(
            id = id,
            propertyId = p?.text("id").orEmpty(),
            propertyCode = p?.text("code").orEmpty(),
            propertyTitle = p?.text("title").orEmpty(),
            locality = p?.text("locality").orEmpty(),
            city = p?.text("city").orEmpty(),
            unitId = o.text("unitId").ifBlank { null },
            bedId = o.text("bedId").ifBlank { null },
            scheduledAt = o.text("scheduledAt"),
            endsAt = o.text("endsAt"),
            status = o.text("status").uppercase().ifBlank { "REQUESTED" },
            visitors = o.int("visitors", 1),
            note = o.text("note").ifBlank { o.text("visitorNote") },
            address = o.text("address"),
            latitude = (loc?.get("latitude") as? JsonPrimitive)?.doubleOrNull,
            longitude = (loc?.get("longitude") as? JsonPrimitive)?.doubleOrNull,
            hostName = host?.text("fullName").orEmpty(),
            hostPhone = host?.text("phoneNumber").orEmpty(),
            hostId = host?.text("id").orEmpty(),
            instructions = o.text("instructions"),
            rejectReason = o.text("rejectReason"),
            cancelReason = o.text("cancelReason"),
            cancelledBy = o.text("cancelledBy").uppercase(),
            canCancel = o.bool("canCancel"),
            canGiveFeedback = o.bool("canGiveFeedback"),
            feedback = fb?.let { VisitFeedback(it.int("rating", 0), it.bool("interested"), it.text("comment"), it.text("at")) },
            source = o.text("source").uppercase(),
            visitorName = visitor?.text("fullName").orEmpty(),
            visitorPhone = visitor?.text("phoneNumber").orEmpty(),
            outcome = o.text("outcome"),
            confirmedAt = o.text("confirmedAt"),
            completedAt = o.text("completedAt"),
            createdAt = o.text("createdAt"),
        )
    }
}
