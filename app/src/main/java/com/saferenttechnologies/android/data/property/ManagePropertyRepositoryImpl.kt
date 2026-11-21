package com.saferenttechnologies.android.data.property

import com.saferenttechnologies.android.domain.model.RevisionStatus
import com.saferenttechnologies.android.domain.model.RevisionResult
import com.saferenttechnologies.android.domain.model.PropertyRevision
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.asText
import com.saferenttechnologies.android.data.mapper.int
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.mapper.toDomain
import com.saferenttechnologies.android.data.mapper.toPropertyError
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.ManagePropertyApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.data.remote.dto.PropertyDataDto
import com.saferenttechnologies.android.domain.model.ActionResult
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ApplicationDecision
import com.saferenttechnologies.android.domain.model.CreateInvitation
import com.saferenttechnologies.android.domain.model.Invitation
import com.saferenttechnologies.android.domain.model.InvitationCreated
import com.saferenttechnologies.android.domain.model.InvitationLink
import com.saferenttechnologies.android.domain.model.Lease
import com.saferenttechnologies.android.domain.model.Page
import com.saferenttechnologies.android.domain.model.PropertyDetail
import com.saferenttechnologies.android.domain.model.PropertyFilter
import com.saferenttechnologies.android.domain.model.PropertyListItem
import com.saferenttechnologies.android.domain.model.PropertyPermissions
import com.saferenttechnologies.android.domain.model.PropertySummary
import com.saferenttechnologies.android.domain.model.RentalApplication
import com.saferenttechnologies.android.domain.model.StatusChange
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManagePropertyRepositoryImpl @Inject constructor(
    private val api: ManagePropertyApi,
    private val safeApiCall: SafeApiCall,
    private val json: Json,
) : ManagePropertyRepository {

    override suspend fun summary(): AppResult<PropertySummary> = call("GET properties/summary", { api.summary() }) { data, _ ->
        val o = data as? JsonObject ?: return@call null
        val props = o["properties"] as? JsonObject
        val units = o["units"] as? JsonObject
        val rent = o["rent"] as? JsonObject
        PropertySummary(
            total = props?.int("total", 0) ?: 0,
            byStatus = props?.entries?.filter { it.key != "total" }
                ?.associate { (k, v) -> k to ((v as? JsonPrimitive)?.intOrNull ?: 0) }.orEmpty(),
            unitsAvailable = units?.int("available", 0) ?: 0,
            unitsOccupied = units?.int("occupied", 0) ?: 0,
            rentPeriod = rent?.text("period").orEmpty(),
            rentExpected = rent?.text("expected")?.ifBlank { "0" } ?: "0",
            rentCollected = rent?.text("collected")?.ifBlank { "0" } ?: "0",
            rentOverdue = rent?.text("overdue")?.ifBlank { "0" } ?: "0",
            overdueCount = rent?.int("overdueCount", 0) ?: 0,
            unreadNotifications = o.int("unreadNotifications", 0),
            changesUnderVerification = (o["changes"] as? JsonObject)?.int("underVerification", 0) ?: 0,
            changesDraftOrRejected = (o["changes"] as? JsonObject)?.int("draftOrRejected", 0) ?: 0,
        )
    }

    override suspend fun list(filter: PropertyFilter, query: String, page: Int): AppResult<Page<PropertyListItem>> {
        val params = buildMap {
            put("page", page.toString())
            put("pageSize", PAGE_SIZE.toString())
            put("sort", "updated")
            filter.publicationStatus?.let { put("publicationStatus", it) }
            filter.activeStatus?.let { put("activeStatus", it) }
            if (filter == PropertyFilter.ARCHIVED) put("includeArchived", "true")
            if (filter == PropertyFilter.AVAILABLE_UNITS) put("hasAvailableUnits", "true")
            query.trim().takeIf { it.isNotEmpty() }?.let { put("q", it) }
        }
        val result = call("GET properties (list)", { api.list(params) }) { data, meta ->
            toPage(data, meta, page) { o ->
                PropertyListItem(
                    id = o.text("id"),
                    code = o.text("code"),
                    title = o.text("title"),
                    name = o.text("name"),
                    propertyTypeLabel = o.text("propertyTypeLabel").ifBlank { o.text("propertyType") },
                    listingType = o.text("listingType"),
                    locality = o.text("locality"),
                    city = o.text("city"),
                    activeStatus = o.text("activeStatus").ifBlank { "ACTIVE" },
                    publicationStatus = o.text("publicationStatus").ifBlank { "DRAFT" },
                    completenessScore = o.int("completenessScore", 0),
                    rentFrom = o.text("rentFrom"),
                    rentTo = o.text("rentTo"),
                    availableCount = o.int("availableCount", 0),
                    occupiedCount = o.int("occupiedCount", 0),
                    coverImageId = o.text("coverImageId").ifBlank { null },
                    wizardStep = o.int("wizardStep", 1),
                    hasDraftChanges = (o["hasDraftChanges"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    changesStatus = RevisionStatus.of(o.text("changesStatus")),
                )
            }
        }
        // Until the server filters by hasAvailableUnits, drop properties without a free flat / bed here.
        return if (filter == PropertyFilter.AVAILABLE_UNITS && result is AppResult.Success) {
            AppResult.Success(result.data.copy(items = result.data.items.filter { it.availableCount > 0 }))
        } else {
            result
        }
    }

    override suspend fun detail(id: String): AppResult<PropertyDetail> = call("GET property detail", { api.detail(id) }) { data, _ ->
        data.property?.toDomain()?.let {
            val can = data.can
            PropertyDetail(
                property = it,
                can = PropertyPermissions(
                    manage = can?.manage ?: false,
                    review = can?.review ?: false,
                    inspect = can?.inspect ?: false,
                    editRevision = can?.editRevision ?: false,
                    submitRevision = can?.submitRevision ?: false,
                    discardRevision = can?.discardRevision ?: false,
                ),
                pendingRevision = revisionOf(data.pendingRevision),
                lastRevision = revisionOf(data.lastRevision),
                approval = approvalOf(data.approval),
            )
        }
    }

    override suspend fun submitChanges(id: String): AppResult<RevisionResult> =
        withMessage("POST revision/submit", { api.submitRevision(id) }) { data, message ->
            RevisionResult(message, revisionOf((data as? JsonObject)?.get("pendingRevision")))
        }

    override suspend fun discardChanges(id: String): AppResult<RevisionResult> =
        withMessage("DELETE revision", { api.discardRevision(id) }) { data, message ->
            RevisionResult(message, revisionOf((data as? JsonObject)?.get("pendingRevision")))
        }

    override suspend fun publish(id: String) = propertyAction("POST publish") { api.publish(id) }
    override suspend fun unpublish(id: String) = propertyAction("POST unpublish") { api.unpublish(id) }
    override suspend fun submit(id: String) = propertyAction("POST submit") { api.submit(id) }

    override suspend fun setActiveStatus(id: String, active: Boolean) = propertyAction("POST status") {
        api.setActiveStatus(id, buildJsonObject { put("activeStatus", if (active) "ACTIVE" else "INACTIVE") })
    }

    override suspend fun archiveOrDelete(id: String): AppResult<ActionResult> =
        withMessage("DELETE property", { api.archiveOrDelete(id) }) { data, message ->
            val o = data as? JsonObject
            ActionResult(
                message = message,
                deleted = (o?.get("deleted") as? JsonPrimitive)?.booleanOrNull ?: false,
                archived = (o?.get("archived") as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }

    override suspend fun setUnitAvailability(id: String, unitId: String, version: Int, status: String) =
        withMessage("PATCH unit availability", {
            api.updateUnit(id, unitId, buildJsonObject {
                put("version", version)
                put("availabilityStatus", status)
            })
        }) { _, message -> ActionResult(message) }

    override suspend fun setBedAvailability(id: String, roomId: String, bedId: String, version: Int, status: String) =
        withMessage("PATCH bed availability", {
            api.updateBed(id, roomId, bedId, buildJsonObject {
                put("version", version)
                put("availabilityStatus", status)
            })
        }) { _, message -> ActionResult(message) }

    override suspend fun leases(propertyId: String, active: Boolean): AppResult<List<Lease>> = leaseList(propertyId, active)

    override suspend fun allLeases(active: Boolean): AppResult<List<Lease>> = leaseList(null, active)

    /**
     * Current = ACTIVE. Past = every closed lease: ENDED (term over / exit done) AND TERMINATED
     * (closed early). Only asking for ENDED page 1 missed terminated leases and anything after page 1.
     */
    private suspend fun leaseList(propertyId: String?, active: Boolean): AppResult<List<Lease>> {
        val statuses = if (active) listOf("ACTIVE") else listOf("ENDED", "TERMINATED")
        val all = mutableListOf<Lease>()
        for (status in statuses) {
            var page = 1
            while (true) {
                when (val r = leasePage(propertyId, status, page)) {
                    is AppResult.Failure -> if (all.isEmpty() && status == statuses.first()) return AppResult.Failure(r.error) else break
                    is AppResult.Success -> {
                        all += r.data.first
                        if (page >= r.data.second || r.data.first.isEmpty() || page >= MAX_LEASE_PAGES) break
                        page++
                    }
                }
            }
        }
        val unique = all.distinctBy { it.id }
        return AppResult.Success(if (active) unique else unique.sortedByDescending { it.exitDate ?: it.endDate })
    }

    /** One page of leases + totalPages. */
    private suspend fun leasePage(propertyId: String?, status: String, page: Int): AppResult<Pair<List<Lease>, Int>> =
        call("GET leases", { api.leases(propertyId, status, page) }) { data, meta ->
            val pages = (meta as? JsonObject)?.int("totalPages", page) ?: page
            data.arrayAt("items", "leases").mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val tenant = o["tenant"] as? JsonObject
                val property = o["property"] as? JsonObject
                val exit = o["exit"] as? JsonObject
                Lease(
                    id = o.text("id"),
                    status = o.text("status"),
                    place = o.text("place"),
                    placeCode = o.text("placeCode"),
                    unitId = o.text("unitId").ifBlank { null },
                    bedId = o.text("bedId").ifBlank { null },
                    monthlyRent = o.text("monthlyRent"),
                    securityDeposit = o.text("securityDeposit"),
                    rentDueDay = (o["rentDueDay"] as? JsonPrimitive)?.intOrNull,
                    startDate = o.text("startDate"),
                    endDate = o.text("endDate"),
                    tenantName = tenant?.text("fullName").orEmpty(),
                    tenantPhone = tenant?.text("phoneNumber")?.ifBlank { null },
                    exitDate = exit?.let { x -> x.text("moveOutDate").ifBlank { x.text("requestedAt").ifBlank { x.text("date") } } },
                    propertyId = o.text("propertyId").ifBlank { property?.text("id").orEmpty() },
                    propertyTitle = property?.text("title").orEmpty(),
                    propertyType = property?.text("propertyType").orEmpty().ifBlank { o.text("propertyType") }.uppercase(),
                    propertyTypeLabel = property?.text("propertyTypeLabel").orEmpty().ifBlank { o.text("propertyTypeLabel") },
                )
            } to pages
        }

    override suspend fun invitations(propertyId: String, status: String): AppResult<List<Invitation>> =
        call("GET invitations", { api.invitations(propertyId, status) }) { data, _ ->
            data.arrayAt("items", "invitations").mapNotNull { (it as? JsonObject)?.toInvitation() }
        }

    override suspend fun createInvitation(propertyId: String, input: CreateInvitation): AppResult<InvitationCreated> {
        val body = buildJsonObject {
            input.unitId?.let { put("unitId", it) }
            input.bedId?.let { put("bedId", it) }
            put("recipientName", input.recipientName.trim())
            if (input.recipientPhone.isNotBlank()) put("recipientPhone", input.recipientPhone.trim())
            input.recipientUserId?.let { put("recipientUserId", it) }
            put("recipientEmail", input.recipientEmail.trim())
            input.monthlyRent.toDoubleOrNull()?.let { put("monthlyRent", it) }
            input.securityDeposit.toDoubleOrNull()?.let { put("securityDeposit", it) }
            input.maintenanceCharges.toDoubleOrNull()?.let { put("maintenanceCharges", it) }
            put("startDate", input.startDate)
            input.endDate.takeIf { it.isNotBlank() }?.let { put("endDate", it) }
            input.rentDueDay?.let { put("rentDueDay", it) }
            input.noticePeriodDays?.let { put("noticePeriodDays", it) }
            input.lockInMonths?.let { put("lockInMonths", it) }
            put("additionalTerms", input.additionalTerms.trim())
            put("expiresInDays", input.expiresInDays)
            input.requirements?.takeIf { it.asksAnything }?.let { r ->
                put(
                    "requirements",
                    buildJsonObject {
                        put("askDetails", r.askDetails)
                        put("companyName", r.companyName)
                        put("references", r.references)
                        put("backgroundVerification", r.backgroundVerification)
                        put("documents", kotlinx.serialization.json.buildJsonArray { r.documents.distinct().take(4).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                    },
                )
            }
        }
        return withMessage("POST invitation", { api.createInvitation(propertyId, body) }) { data, message ->
            val o = data as? JsonObject ?: return@withMessage null
            val invitation = (o["invitation"] as? JsonObject)?.toInvitation() ?: return@withMessage null
            InvitationCreated(message, invitation, (o["link"] as? JsonObject)?.toLink())
        }
    }

    override suspend fun renewInvitationLink(invitationId: String, expiresInDays: Int): AppResult<InvitationLink> =
        call("POST invitation link", { api.renewInvitationLink(invitationId, buildJsonObject { put("expiresInDays", expiresInDays) }) }) { data, _ ->
            val o = data as? JsonObject ?: return@call null
            ((o["link"] as? JsonObject) ?: o).toLink()
        }

    override suspend fun tenantDetails(invitationId: String): AppResult<com.saferenttechnologies.android.domain.model.TenantDetailsView> =
        call("GET invitation tenant details", { api.tenantDetails(invitationId) }) { data, _ ->
            val o = data as? JsonObject ?: return@call null
            val d = o["details"] as? JsonObject
            fun JsonObject.boolOrNull(k: String) = (this[k] as? JsonPrimitive)?.let { p -> if (p is kotlinx.serialization.json.JsonNull) null else p.content.toBooleanStrictOrNull() }
            fun JsonObject.intOrNullOf(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
            com.saferenttechnologies.android.domain.model.TenantDetailsView(
                requirements = com.saferenttechnologies.android.data.invitation.InvitationMapper.requirements(o["requirements"]),
                details = d?.let {
                    com.saferenttechnologies.android.domain.model.AnsweredDetails(
                        tenantType = it.text("tenantType").uppercase(),
                        employmentType = it.text("employmentType").uppercase(),
                        companyName = it.text("companyName"),
                        adults = it.intOrNullOf("adults"),
                        children = it.intOrNullOf("children"),
                        hasPets = it.boolOrNull("hasPets"),
                        petDetails = it.text("petDetails"),
                        smokes = it.boolOrNull("smokes"),
                        stayMonths = it.intOrNullOf("stayMonths"),
                        references = (it["references"] as? JsonArray)?.mapNotNull { r ->
                            (r as? JsonObject)?.let { x -> com.saferenttechnologies.android.domain.model.TenantReference(x.text("name"), x.text("relation"), x.text("phone")) }
                        }.orEmpty(),
                        backgroundConsentAt = it.text("backgroundConsentAt"),
                        leaseActive = it.boolOrNull("leaseActive") ?: true,
                        answeredAt = it.text("answeredAt"),
                    )
                },
                documents = (o["documents"] as? JsonArray)?.mapNotNull { x ->
                    (x as? JsonObject)?.let { com.saferenttechnologies.android.domain.model.DocShareState(it.text("key"), it.text("label"), it.boolOrNull("shared") == true) }
                }.orEmpty(),
                conflicts = (o["conflicts"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
            )
        }

    override suspend fun revokeInvitation(invitationId: String) =
        withMessage("POST invitation revoke", { api.revokeInvitation(invitationId) }) { _, message -> ActionResult(message) }

    override suspend fun applications(propertyId: String, status: String): AppResult<List<RentalApplication>> =
        call("GET applications", { api.applications(propertyId, status) }) { data, _ ->
            data.arrayAt("items", "applications").mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val tenant = o["tenant"] as? JsonObject
                val profile = tenant?.get("tenantProfile") as? JsonObject
                RentalApplication(
                    id = o.text("id"),
                    status = o.text("status"),
                    proposedMoveIn = o.text("proposedMoveIn"),
                    occupants = (o["occupants"] as? JsonPrimitive)?.intOrNull,
                    message = o.text("message"),
                    unitNumber = (o["unit"] as? JsonObject)?.text("unitNumber").orEmpty(),
                    bedNumber = (o["bed"] as? JsonObject)?.text("bedNumber").orEmpty(),
                    tenantName = tenant?.text("fullName").orEmpty(),
                    occupation = profile?.text("occupation").orEmpty(),
                    familySize = (profile?.get("familySize") as? JsonPrimitive)?.intOrNull,
                    currentCity = profile?.text("currentCity").orEmpty(),
                    createdAt = o.text("createdAt"),
                )
            }
        }

    override suspend fun decideApplication(applicationId: String, decision: ApplicationDecision): AppResult<ActionResult> {
        val body = buildJsonObject {
            put("decision", if (decision.approve) "APPROVE" else "REJECT")
            if (decision.approve) {
                put("startDate", decision.startDate)
                decision.months?.let { put("months", it) }
                decision.rentDueDay?.let { put("rentDueDay", it) }
            }
            put("note", decision.note.trim())
        }
        return withMessage("POST application decision", { api.decideApplication(applicationId, body) }) { _, message -> ActionResult(message) }
    }

    override suspend fun statusHistory(propertyId: String, page: Int): AppResult<Page<StatusChange>> =
        call("GET status-history", { api.statusHistory(propertyId, page) }) { data, meta ->
            toPage(data, meta, page) { o ->
                StatusChange(
                    id = o.text("id"),
                    entityType = o.text("entityType"),
                    field = o.text("field"),
                    from = o.text("from"),
                    to = o.text("to"),
                    reason = o.text("reason"),
                    createdAt = o.text("createdAt"),
                    actorName = (o["actor"] as? JsonObject)?.text("fullName").orEmpty(),
                )
            }
        }

    // ---------------- helpers ----------------

    private fun JsonObject.toInvitation() = Invitation(
        id = text("id"),
        code = text("code"),
        target = text("target"),
        status = text("status"),
        recipientName = text("recipientName"),
        monthlyRent = text("monthlyRent"),
        expiresAt = text("expiresAt"),
        unitId = text("unitId").ifBlank { (this["unit"] as? JsonObject)?.text("id").orEmpty() }.ifBlank { null },
        bedId = text("bedId").ifBlank { (this["bed"] as? JsonObject)?.text("id").orEmpty() }.ifBlank { null },
    )

    private fun JsonObject.toLink(): InvitationLink? = text("url").ifBlank { null }?.let { InvitationLink(it, text("expiresAt"), text("shareText")) }

    private fun <T> toPage(data: JsonElement, meta: JsonElement?, requested: Int, item: (JsonObject) -> T): Page<T> {
        val m = meta as? JsonObject
        val items = data.arrayAt("items").mapNotNull { (it as? JsonObject)?.let(item) }
        val page = m?.int("page", requested) ?: requested
        return Page(items, page, m?.int("totalPages", page) ?: page, m?.int("total", items.size) ?: items.size)
    }

    private suspend fun propertyAction(name: String, request: suspend () -> Response<ApiEnvelope<PropertyDataDto>>): AppResult<ActionResult> =
        withMessage(name, request) { data, message -> ActionResult(message, data?.property?.toDomain()) }

    private suspend fun <T, R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<T>>,
        map: (data: T, meta: JsonElement?) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> r.data?.let { map(it, r.meta) }?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        is ApiResult.Failure -> AppResult.Failure(r.error.toPropertyError(json))
    }

    /** Like [call] but hands the server `message` (shown in a snackbar) to [map]; data may be null. */
    private suspend fun <T, R> withMessage(
        name: String,
        request: suspend () -> Response<ApiEnvelope<T>>,
        map: (data: T?, message: String) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> map(r.data, r.message)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        is ApiResult.Failure -> AppResult.Failure(r.error.toPropertyError(json))
    }

    override suspend fun review(id: String, approve: Boolean, withoutProof: Boolean, reason: String): AppResult<ActionResult> =
        withMessage("POST property review", {
            api.review(id, buildJsonObject {
                put("decision", if (approve) "APPROVE" else "REJECT")
                if (approve && withoutProof) put("withoutOwnershipDocument", true)
                if (!approve) put("reason", reason.trim())
            })
        }) { _, message -> ActionResult(message) }

    private companion object {
        const val MAX_LEASE_PAGES = 10

        /** { ownerVerified, ownerKycSubmitted, ownershipProofVerified, blockers[], canApprove, … } | null */
        fun approvalOf(e: JsonElement?): com.saferenttechnologies.android.domain.model.PropertyApproval? {
            val o = e as? JsonObject ?: return null
            fun b(k: String) = (o[k] as? JsonPrimitive)?.content.equals("true", true)
            return com.saferenttechnologies.android.domain.model.PropertyApproval(
                ownerVerified = b("ownerVerified"),
                ownerKycSubmitted = b("ownerKycSubmitted"),
                ownershipProofVerified = b("ownershipProofVerified"),
                blockers = (o["blockers"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { x ->
                    val bo = x as? JsonObject ?: return@mapNotNull null
                    com.saferenttechnologies.android.domain.model.ApprovalBlocker(
                        code = bo.text("code").uppercase(),
                        message = bo.text("message"),
                        ownerKycSubmitted = (bo["ownerKycSubmitted"] as? JsonPrimitive)?.content.equals("true", true),
                    )
                },
                canApprove = b("canApprove"),
                canApproveWithoutProof = b("canApproveWithoutProof"),
                confirmTitle = o.text("confirmTitle"),
                confirmMessage = o.text("confirmMessage"),
            )
        }
        const val PAGE_SIZE = 20

        /** { id, status, changedSections[], createdAt, submittedAt, decidedAt, moderationNote } | null. */
        fun revisionOf(e: JsonElement?): PropertyRevision? {
            val o = e as? JsonObject ?: return null
            val status = RevisionStatus.of(o.text("status")) ?: return null
            return PropertyRevision(
                id = o.text("id"),
                status = status,
                changedSections = o.arrayAt("changedSections").mapNotNull { it.asText() },
                createdAt = o.text("createdAt"),
                submittedAt = o.text("submittedAt").ifBlank { null },
                decidedAt = o.text("decidedAt").ifBlank { null },
                moderationNote = o.text("moderationNote").ifBlank { null },
            )
        }
    }
}
