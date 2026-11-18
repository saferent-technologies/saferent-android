package com.saferenttechnologies.android.data.lease

import com.saferenttechnologies.android.domain.model.DepositRefund
import com.saferenttechnologies.android.domain.model.PendingReview
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.asText
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.LeaseApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.LeaseExitRequest
import com.saferenttechnologies.android.domain.model.LeaseExitState
import com.saferenttechnologies.android.domain.model.LeaseReviewInfo
import com.saferenttechnologies.android.domain.model.LeaseSummary
import com.saferenttechnologies.android.domain.model.ReviewCategory
import com.saferenttechnologies.android.domain.model.ReviewDraft
import com.saferenttechnologies.android.domain.model.ReviewForm
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.repository.LeaseActionResult
import com.saferenttechnologies.android.domain.repository.LeaseRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaseRepositoryImpl @Inject constructor(
    private val api: LeaseApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
) : LeaseRepository {

    override suspend fun lease(leaseId: String): AppResult<LeaseSummary> =
        call("GET lease", { api.lease(leaseId) }) { data, _ -> (data as? JsonObject)?.let { LeaseExitMapper.lease(it, leaseId) } }

    override suspend fun myLeases(): AppResult<List<LeaseSummary>> =
        call("GET leases (tenant)", { api.mine() }) { data, _ ->
            data.arrayAt("leases", "items").mapNotNull { (it as? JsonObject)?.let { o -> LeaseExitMapper.lease(o, "") } }.filter { it.id.isNotBlank() }
        }

    override suspend fun exitState(leaseId: String): AppResult<LeaseExitState> =
        call("GET lease exit", { api.exit(leaseId) }) { data, _ -> (data as? JsonObject)?.let(LeaseExitMapper::state) }

    override suspend fun requestExit(leaseId: String, reason: String, moveOut: SimpleDate, review: ReviewDraft?): AppResult<LeaseActionResult> {
        logger.d(TAG, "request close (review=${review != null})")
        return action("POST lease exit") {
            api.requestExit(leaseId, buildJsonObject {
                put("reason", reason.trim())
                put("moveOutDate", moveOut.iso())
                review?.let { put("review", LeaseExitMapper.reviewJson(it)) }
            })
        }
    }

    override suspend fun acceptExit(leaseId: String, note: String, review: ReviewDraft?): AppResult<LeaseActionResult> {
        logger.d(TAG, "accept close (review=${review != null})")
        return action("POST lease exit/accept") {
            api.acceptExit(leaseId, buildJsonObject {
                if (note.isNotBlank()) put("note", note.trim())
                review?.let { put("review", LeaseExitMapper.reviewJson(it)) }
            })
        }
    }

    override suspend fun declineExit(leaseId: String, note: String): AppResult<LeaseActionResult> {
        logger.d(TAG, "decline close")
        return action("POST lease exit/decline") { api.declineExit(leaseId, buildJsonObject { put("note", note.trim()) }) }
    }

    override suspend fun cancelExit(leaseId: String): AppResult<LeaseActionResult> {
        logger.d(TAG, "cancel close")
        return action("POST lease exit/cancel") { api.cancelExit(leaseId, buildJsonObject { }) }
    }

    override suspend fun reviewInfo(leaseId: String): AppResult<LeaseReviewInfo> =
        call("GET lease review", { api.review(leaseId) }) { data, _ -> LeaseExitMapper.reviewInfo(data) }

    override suspend fun postReview(leaseId: String, review: ReviewDraft): AppResult<LeaseActionResult> =
        action("POST lease review") { api.postReview(leaseId, LeaseExitMapper.reviewJson(review)) }

    override suspend fun pendingReviews(): AppResult<List<PendingReview>> =
        call("GET reviews/pending", { api.pendingReviews() }) { data, _ -> data.arrayAt("items", "reviews", "leases").mapNotNull { (it as? JsonObject)?.let(LeaseExitMapper::pending) } }

    override suspend fun depositRefund(leaseId: String, answer: DepositRefund, amount: String): AppResult<LeaseActionResult> =
        action("POST lease deposit-refund") {
            api.depositRefund(leaseId, buildJsonObject {
                put("refunded", answer.name)
                if (answer == DepositRefund.PARTIAL) amount.toBigDecimalOrNull()?.let { put("amount", it.toDouble()) }
            })
        }

    // ---------------- plumbing ----------------

    private suspend fun <R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<JsonElement>>,
        map: (data: JsonElement, meta: JsonElement?) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> map(r.data ?: JsonNull, r.meta)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        is ApiResult.Failure -> AppResult.Failure(r.error)
    }

    private suspend fun action(name: String, request: suspend () -> Response<ApiEnvelope<JsonElement>>): AppResult<LeaseActionResult> =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success -> AppResult.Success(LeaseActionResult(r.message, LeaseExitMapper.closed(r.data)))
            is ApiResult.NotModified -> AppResult.Success(LeaseActionResult(""))
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    private companion object {
        const val TAG = "SR-Lease"
    }
}

/** JSON → lease close models (pure, unit-tested). Lenient: missing keys → defaults. */
object LeaseExitMapper {
    fun state(o: JsonObject) = LeaseExitState(
        leaseStatus = o.text("leaseStatus").ifBlank { "ACTIVE" }.uppercase(),
        side = o.text("side").ifBlank { null }?.uppercase(),
        noticePeriodDays = o.intOrNull("noticePeriodDays"),
        endDate = o.text("endDate").ifBlank { null },
        canRequest = o.bool("canRequest"),
        open = (o["open"] as? JsonObject)?.let(::request),
        history = o.arrayAt("history").mapNotNull { (it as? JsonObject)?.let(::request) },
    )

    fun request(o: JsonObject) = LeaseExitRequest(
        id = o.text("id"),
        leaseId = o.text("leaseId"),
        requestedByRole = o.text("requestedByRole").uppercase(),
        requestedBy = o.text("requestedBy"),
        reason = o.text("reason"),
        moveOutDate = o.text("moveOutDate"),
        status = o.text("status").ifBlank { "PENDING" }.uppercase(),
        respondedBy = o.text("respondedBy").ifBlank { null },
        respondedAt = o.text("respondedAt").ifBlank { null },
        responseNote = o.text("responseNote").ifBlank { null },
        completedAt = o.text("completedAt").ifBlank { null },
        createdAt = o.text("createdAt"),
        hasMyReview = o.bool("hasMyReview"),
        canRespond = o.bool("canRespond"),
        canCancel = o.bool("canCancel"),
    )

    /** GET leases/{id}: `{ lease: {…} }` or the lease itself; property / tenant / landlord nested. */
    fun lease(root: JsonObject, id: String): LeaseSummary {
        val l = root.obj("lease") ?: root
        val prop = l.obj("property")
        val unit = l.obj("unit")
        val bed = l.obj("bed")
        val other = l.obj("counterparty") ?: l.obj("landlord") ?: l.obj("tenant")
        return LeaseSummary(
            id = l.text("id").ifBlank { id },
            status = l.text("status").uppercase(),
            propertyTitle = prop?.text("title").orEmpty(),
            place = l.text("place").ifBlank {
                listOfNotNull(unit?.text("unitNumber"), bed?.text("bedNumber"), prop?.text("city")).filter { it.isNotBlank() }.joinToString(" · ")
            },
            monthlyRent = l.text("monthlyRent"),
            securityDeposit = l.text("securityDeposit"),
            rentDueDay = l.intOrNull("rentDueDay"),
            startDate = l.text("startDate"),
            endDate = l.text("endDate"),
            otherName = other?.text("fullName").orEmpty().ifBlank { other?.text("name").orEmpty() },
            address = l.text("address").ifBlank {
                listOf(prop?.text("addressLine").orEmpty(), prop?.text("locality").orEmpty(), prop?.text("city").orEmpty(), prop?.text("pincode").orEmpty())
                    .filter { it.isNotBlank() }.joinToString(", ")
            },
            otherPhone = other?.text("phone").orEmpty(),
            endedOn = l.text("endedAt").ifBlank { l.text("closedAt") }.ifBlank { l.text("exitDate") },
        )
    }

    /** GET leases/{id}/review → { eligible, myReview, form: { categories, tags } }. */
    fun reviewInfo(data: JsonElement): LeaseReviewInfo? {
        val o = data as? JsonObject ?: return null
        val form = o.obj("form")
        return LeaseReviewInfo(
            eligible = o.bool("eligible"),
            hasMyReview = o["myReview"].let { it != null && it !is JsonNull },
            form = ReviewForm(
                categories = form?.arrayAt("categories")?.mapNotNull { c ->
                    val co = c as? JsonObject ?: return@mapNotNull null
                    co.text("key").ifBlank { null }?.let { ReviewCategory(it, co.text("label").ifBlank { it }, co.bool("required")) }
                }.orEmpty(),
                tags = form?.arrayAt("tags")?.mapNotNull { it.asText()?.ifBlank { null } }.orEmpty(),
            ),
        )
    }

    /** { ratings: { overall: 4, … }, text?, tags? } — only categories that were rated. */
    fun reviewJson(r: ReviewDraft): JsonObject = buildJsonObject {
        put("ratings", buildJsonObject { r.ratings.filterValues { it in 1..5 }.forEach { (k, v) -> put(k, v) } })
        if (r.text.isNotBlank()) put("text", r.text.trim().take(ReviewDraft.MAX_TEXT))
        if (r.tags.isNotEmpty()) put("tags", buildJsonArray { r.tags.take(ReviewDraft.MAX_TAGS).forEach { add(JsonPrimitive(it)) } })
    }

    /** { leaseId | lease:{id}, property:{id,title}, place, counterparty|landlord|tenant:{fullName}, endedAt, deposit } */
    fun pending(o: JsonObject): PendingReview? {
        val lease = o.obj("lease")
        val id = o.text("leaseId").ifBlank { lease?.text("id").orEmpty() }.ifBlank { return null }
        val prop = o.obj("property") ?: lease?.obj("property")
        val other = o.obj("counterparty") ?: o.obj("landlord") ?: o.obj("tenant")
        return PendingReview(
            leaseId = id,
            propertyId = o.text("propertyId").ifBlank { prop?.text("id").orEmpty() },
            propertyTitle = prop?.text("title").orEmpty(),
            place = o.text("place").ifBlank { lease?.text("place").orEmpty() },
            otherName = other?.text("fullName").orEmpty().ifBlank { o.text("otherName") },
            endedAt = o.text("endedAt").ifBlank { lease?.text("endDate").orEmpty() },
            depositAmount = o.text("securityDeposit").ifBlank { lease?.text("securityDeposit").orEmpty() },
        )
    }

    fun closed(data: JsonElement?): Boolean = (data as? JsonObject)?.bool("closed") == true

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false
    private fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }?.toInt()
}
