package com.saferenttechnologies.android.data.society

import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.owner.OwnerMapper
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SocietyApi
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.CommonArea
import com.saferenttechnologies.android.domain.model.ComplianceRules
import com.saferenttechnologies.android.domain.model.LeaseSociety
import com.saferenttechnologies.android.domain.model.ListingMove
import com.saferenttechnologies.android.domain.model.ListingSociety
import com.saferenttechnologies.android.domain.model.ParkingSlot
import com.saferenttechnologies.android.domain.model.ParkingSummary
import com.saferenttechnologies.android.domain.model.RulesDocument
import com.saferenttechnologies.android.domain.model.SafetyRules
import com.saferenttechnologies.android.domain.model.Society
import com.saferenttechnologies.android.domain.model.SocietyApproval
import com.saferenttechnologies.android.domain.model.SocietyData
import com.saferenttechnologies.android.domain.model.SocietyRule
import com.saferenttechnologies.android.domain.model.SocietyRules
import com.saferenttechnologies.android.domain.repository.SocietyRepository
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
class SocietyRepositoryImpl @Inject constructor(
    private val api: SocietyApi,
    private val safeApiCall: SafeApiCall,
) : SocietyRepository {

    override suspend fun society(propertyId: String): AppResult<SocietyData> =
        call("GET society", { api.society(propertyId) }) { d, _ -> SocietyMapper.data(d) }

    override suspend fun saveSociety(propertyId: String, society: Society) =
        call("PUT society", { api.saveSociety(propertyId, SocietyMapper.body(society)) }) { d, m -> m to SocietyMapper.data(d) }

    override suspend fun addSlot(propertyId: String, slot: ParkingSlot) =
        call("POST parking slot", { api.addSlot(propertyId, SocietyMapper.slotBody(slot)) }) { d, _ -> SocietyMapper.slotOf(d) }

    override suspend fun updateSlot(propertyId: String, slotId: String, changes: Map<String, Any?>) =
        call("PATCH parking slot", { api.updateSlot(propertyId, slotId, SocietyMapper.changesBody(changes)) }) { d, _ -> SocietyMapper.slotOf(d) }

    override suspend fun deleteSlot(propertyId: String, slotId: String): AppResult<Unit> =
        call("DELETE parking slot", { api.deleteSlot(propertyId, slotId) }) { _, _ -> Unit }

    override suspend fun shareDocument(documentId: String, shared: Boolean): AppResult<String> =
        call("PATCH document share", { api.updateDocument(documentId, buildJsonObject { put("sharedWithTenant", shared) }) }) { _, m -> m }

    override suspend fun leaseSociety(leaseId: String): AppResult<LeaseSociety> =
        call("GET lease society", { api.leaseSociety(leaseId) }) { d, _ -> SocietyMapper.lease(d) }

    override suspend fun saveApproval(leaseId: String, approval: SocietyApproval) =
        call("PUT society approval", {
            api.saveApproval(leaseId, buildJsonObject {
                put("status", approval.status)
                put("date", approval.date?.takeIf { approval.status == "APPROVED" || approval.status == "REJECTED" })
                put("note", approval.note.trim().ifBlank { null })
            })
        }) { d, m -> m to SocietyMapper.lease(d) }

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

/** JSON ⇄ society models (pure, unit-tested). */
object SocietyMapper {
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.boolOrNull(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { it.booleanOrNull ?: it.content.lowercase().toBooleanStrictOrNull() }
    private fun JsonObject.intOrNull(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
    private fun JsonObject.enumOrNull(k: String) = text(k).trim().uppercase().ifBlank { null }
    private fun JsonObject.strings(k: String) = arrayAt(k).mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content?.uppercase() }

    private fun rules(o: JsonObject) = o.arrayAt("rules").mapNotNull { e -> (e as? JsonObject)?.let { SocietyRule(it.text("title"), it.text("description")) } }
    private fun areas(o: JsonObject) = o.arrayAt("commonAreas").mapNotNull { e -> (e as? JsonObject)?.let { CommonArea(it.text("name"), it.text("timings"), it.text("notes")) } }

    fun society(o: JsonObject?): Society? = o?.let {
        Society(
            isSociety = it.boolOrNull("isSociety") ?: true,
            associationName = it.text("associationName"),
            registrationNumber = it.text("registrationNumber"),
            contactName = it.text("contactName"),
            contactRole = it.enumOrNull("contactRole"),
            contactPhone = it.text("contactPhone"),
            contactEmail = it.text("contactEmail"),
            officeHours = it.text("officeHours"),
            maintenanceAmount = ComplianceRules.amountText(it.num("maintenanceAmount")),
            maintenanceFrequency = it.enumOrNull("maintenanceFrequency"),
            maintenanceMonthly = it.num("maintenanceMonthly"),
            maintenancePaidBy = it.enumOrNull("maintenancePaidBy"),
            maintenancePayTo = it.enumOrNull("maintenancePayTo"),
            rules = rules(it),
            visitorPolicy = it.text("visitorPolicy"),
            gateAccessMethods = it.strings("gateAccessMethods"),
            gateContactName = it.text("gateContactName"),
            gateContactPhone = it.text("gateContactPhone"),
            clubhouseRules = it.text("clubhouseRules"),
            commonAreas = areas(it),
            moveRequiresPermission = it.boolOrNull("moveRequiresPermission"),
            moveNoticeDays = it.intOrNull("moveNoticeDays"),
            moveAllowedTimings = it.text("moveAllowedTimings"),
            moveChargeAmount = ComplianceRules.amountText(it.num("moveChargeAmount")),
            moveDepositAmount = ComplianceRules.amountText(it.num("moveDepositAmount")),
            moveLiftBooking = it.boolOrNull("moveLiftBooking"),
            moveNotes = it.text("moveNotes"),
            updatedAt = it.text("updatedAt").ifBlank { null },
        )
    }

    fun slot(o: JsonObject): ParkingSlot? {
        val id = o.text("id").ifBlank { return null }
        return ParkingSlot(
            id = id,
            slotNumber = o.text("slotNumber"),
            unitId = o.text("unitId").ifBlank { null },
            unitLabel = o.text("unitLabel").ifBlank { null },
            vehicleType = o.enumOrNull("vehicleType") ?: "CAR",
            covered = o.boolOrNull("covered") == true,
            notes = o.text("notes"),
        )
    }

    /** { slot } or the slot itself. */
    fun slotOf(d: JsonElement): ParkingSlot? = (d as? JsonObject)?.let { (it.obj("slot") ?: it).let(::slot) }

    private fun rulesDocument(o: JsonObject?) = o?.let { r -> r.text("documentId").ifBlank { null }?.let { RulesDocument(it, r.boolOrNull("sharedWithTenant") == true) } }

    fun data(d: JsonElement): SocietyData {
        val o = d as? JsonObject ?: return SocietyData()
        return SocietyData(
            society = society(o.obj("society")),
            parkingSlots = o.arrayAt("parkingSlots").mapNotNull { (it as? JsonObject)?.let(::slot) },
            rulesDocument = rulesDocument(o.obj("rulesDocument")),
        )
    }

    private fun amount(text: String) = ComplianceRules.amount(text)?.takeUnless { it.isNaN() }

    /** PUT body: the whole object, never maintenanceMonthly / updatedAt. isSociety=false → only that. */
    fun body(raw: Society): JsonObject {
        val s = SocietyRules.clean(raw)
        if (!s.isSociety) return buildJsonObject { put("isSociety", false) }
        return buildJsonObject {
            put("isSociety", true)
            put("associationName", s.associationName.ifBlank { null })
            put("registrationNumber", s.registrationNumber.ifBlank { null })
            put("contactName", s.contactName.ifBlank { null })
            put("contactRole", s.contactRole)
            put("contactPhone", SafetyRules.phoneForApi(s.contactPhone))
            put("contactEmail", s.contactEmail.ifBlank { null })
            put("officeHours", s.officeHours.ifBlank { null })
            put("maintenanceAmount", amount(s.maintenanceAmount))
            put("maintenanceFrequency", s.maintenanceFrequency)
            put("maintenancePaidBy", s.maintenancePaidBy)
            put("maintenancePayTo", s.maintenancePayTo)
            put("rules", buildJsonArray { s.rules.forEach { r -> add(buildJsonObject { put("title", r.title); put("description", r.description) }) } })
            put("visitorPolicy", s.visitorPolicy.ifBlank { null })
            put("gateAccessMethods", buildJsonArray { s.gateAccessMethods.forEach { add(JsonPrimitive(it)) } })
            put("gateContactName", s.gateContactName.ifBlank { null })
            put("gateContactPhone", SafetyRules.phoneForApi(s.gateContactPhone))
            put("clubhouseRules", s.clubhouseRules.ifBlank { null })
            put("commonAreas", buildJsonArray { s.commonAreas.forEach { a -> add(buildJsonObject { put("name", a.name); put("timings", a.timings.ifBlank { null }); put("notes", a.notes.ifBlank { null }) }) } })
            put("moveRequiresPermission", s.moveRequiresPermission)
            put("moveNoticeDays", s.moveNoticeDays)
            put("moveAllowedTimings", s.moveAllowedTimings.ifBlank { null })
            put("moveChargeAmount", amount(s.moveChargeAmount))
            put("moveDepositAmount", amount(s.moveDepositAmount))
            put("moveLiftBooking", s.moveLiftBooking)
            put("moveNotes", s.moveNotes.ifBlank { null })
        }
    }

    fun slotBody(s: ParkingSlot): JsonObject = buildJsonObject {
        put("slotNumber", s.slotNumber.trim())
        put("unitId", s.unitId)
        put("vehicleType", s.vehicleType)
        put("covered", s.covered)
        put("notes", s.notes.trim().ifBlank { null })
    }

    fun changesBody(changes: Map<String, Any?>): JsonObject = buildJsonObject {
        changes.forEach { (k, v) ->
            when (v) {
                null -> put(k, JsonNull)
                is Boolean -> put(k, v)
                is Number -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }

    /** Browse data.property.society (no phones / emails / names). */
    fun listing(o: JsonObject?): ListingSociety? {
        if (o == null) return null
        val move = o.obj("move")?.let {
            ListingMove(it.boolOrNull("requiresPermission"), it.intOrNull("noticeDays"), it.text("allowedTimings"), it.num("charge"), it.num("deposit"), it.boolOrNull("liftBooking"), it.text("notes"))
        }?.takeUnless { it.isEmpty }
        return ListingSociety(
            associationName = o.text("associationName"),
            maintenanceAmount = o.num("maintenanceAmount"),
            maintenanceFrequency = o.enumOrNull("maintenanceFrequency"),
            maintenanceMonthly = o.num("maintenanceMonthly"),
            maintenancePaidBy = o.enumOrNull("maintenancePaidBy"),
            rules = rules(o).filter { it.title.isNotBlank() },
            visitorPolicy = o.text("visitorPolicy"),
            gateAccessMethods = o.strings("gateAccessMethods"),
            clubhouseRules = o.text("clubhouseRules"),
            commonAreas = areas(o).filter { it.name.isNotBlank() },
            move = move,
            parking = o.arrayAt("parking").mapNotNull { e ->
                (e as? JsonObject)?.let { ParkingSummary(it.enumOrNull("vehicleType") ?: "CAR", it.boolOrNull("covered") == true, it.intOrNull("count") ?: 0) }?.takeIf { it.count > 0 }
            },
        )
    }

    fun lease(d: JsonElement): LeaseSociety {
        val o = d as? JsonObject ?: return LeaseSociety()
        val a = o.obj("approval")
        return LeaseSociety(
            society = society(o.obj("society")),
            myParking = o.arrayAt("myParking").mapNotNull { (it as? JsonObject)?.let(::slot) },
            rulesDocument = rulesDocument(o.obj("rulesDocument")),
            approval = SocietyApproval(a?.enumOrNull("status"), a?.text("date")?.take(10)?.ifBlank { null }, a?.text("note").orEmpty()),
            canEditApproval = o.obj("can")?.boolOrNull("editApproval") == true,
        )
    }
}
