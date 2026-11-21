package com.saferenttechnologies.android.data.people

import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.mapper.toPropertyError
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.PeopleApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.LookupQuery
import com.saferenttechnologies.android.domain.model.PastLeaseItem
import com.saferenttechnologies.android.domain.model.PaymentRow
import com.saferenttechnologies.android.domain.model.PaymentStats
import com.saferenttechnologies.android.domain.model.PersonPreview
import com.saferenttechnologies.android.domain.model.TenantReport
import com.saferenttechnologies.android.domain.model.TenantReviewItem
import com.saferenttechnologies.android.domain.repository.PeopleRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeopleRepositoryImpl @Inject constructor(
    private val api: PeopleApi,
    private val safeApiCall: SafeApiCall,
) : PeopleRepository {
    override suspend fun lookup(query: LookupQuery): AppResult<PersonPreview?> =
        call("GET users/lookup", { api.lookup(query.phone(), query.id()) }) { data -> PeopleMapper.person(data) }

    override suspend fun tenantReport(query: LookupQuery): AppResult<TenantReport?> =
        call("GET tenants/report", { api.tenantReport(query.phone(), query.id()) }) { data -> PeopleMapper.report(data) }

    private fun LookupQuery.phone() = (this as? LookupQuery.Phone)?.value
    private fun LookupQuery.id() = (this as? LookupQuery.SafeRentId)?.value

    /** 404 → Success(null): "not on SafeRent". */
    private suspend fun <R> call(name: String, request: suspend () -> Response<ApiEnvelope<JsonElement>>, map: (JsonElement) -> R?): AppResult<R?> =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success -> AppResult.Success(map(r.data ?: JsonNull))
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> if ((r.error as? AppError.Server)?.httpCode == 404) AppResult.Success(null)
            else AppResult.Failure(r.error.toPropertyError(SafeRentJson.instance))
        }
}

/** JSON → people (pure, unit-tested). */
object PeopleMapper {
    fun person(data: JsonElement): PersonPreview? {
        val root = data as? JsonObject ?: return null
        val o = root.obj("user") ?: root.obj("tenant") ?: root
        val id = o.text("id").ifBlank { return null }
        return PersonPreview(
            id = id,
            fullName = o.text("fullName"),
            safeRentId = o.text("safeRentId").ifBlank { o.text("code") },
            phoneMasked = o.text("phoneMasked").ifBlank { o.text("phone") },
            city = o.text("city"),
            verified = o.bool("verified") || o.bool("isVerified"),
            memberSince = o.text("memberSince").ifBlank { o.text("createdAt") },
        )
    }

    fun report(data: JsonElement): TenantReport? {
        val root = data as? JsonObject ?: return null
        val person = person(root) ?: return null
        val stats = root.obj("reviewStats") ?: root.obj("stats")
        val pay = root.obj("payments") ?: root.obj("paymentStats")
        return TenantReport(
            person = person,
            rating = stats?.double("average") ?: root.double("rating"),
            reviewCount = stats?.double("count")?.toInt() ?: root.arrayAt("reviews").size,
            reviews = root.arrayAt("reviews").mapNotNull { r ->
                val o = r as? JsonObject ?: return@mapNotNull null
                TenantReviewItem(
                    rating = o.double("rating") ?: o.double("overallRating"),
                    comment = o.text("comment").ifBlank { o.text("text") },
                    tags = o.arrayAt("tags").mapNotNull { (it as? JsonPrimitive)?.content },
                    landlordName = o.obj("author")?.text("fullName").orEmpty().ifBlank { o.text("landlordName") },
                    place = o.text("place").ifBlank { listOf(o.text("propertyTitle"), o.text("city")).filter { it.isNotBlank() }.joinToString(", ") },
                    createdAt = o.text("createdAt"),
                )
            },
            leases = root.arrayAt("leases").mapNotNull { l ->
                val o = l as? JsonObject ?: return@mapNotNull null
                PastLeaseItem(
                    propertyTitle = o.text("propertyTitle").ifBlank { o.obj("property")?.text("title").orEmpty() },
                    city = o.text("city").ifBlank { o.obj("property")?.text("city").orEmpty() },
                    startDate = o.text("startDate"),
                    endDate = o.text("endDate").ifBlank { o.text("endedAt") },
                    status = o.text("status").uppercase(),
                    monthlyRent = o.text("monthlyRent"),
                )
            },
            payments = PaymentStats(
                total = pay?.double("total")?.toInt() ?: 0,
                onTime = pay?.double("onTime")?.toInt() ?: 0,
                late = pay?.double("late")?.toInt() ?: 0,
                missed = pay?.double("missed")?.toInt() ?: 0,
            ),
            recentPayments = (pay?.arrayAt("recent").orEmpty().ifEmpty { root.arrayAt("recentPayments") }).mapNotNull { p ->
                val o = p as? JsonObject ?: return@mapNotNull null
                PaymentRow(o.text("period").ifBlank { o.text("month") }, o.text("amount"), o.text("status").uppercase(), o.text("paidOn").ifBlank { o.text("paidAt") })
            },
        )
    }

    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.double(k: String): Double? = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
    private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false
}
