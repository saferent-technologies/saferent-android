package com.saferenttechnologies.android.data.invitation

import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.parseIsoMillis
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.InvitationApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.InvitationDecision
import com.saferenttechnologies.android.domain.model.TenantInvitation
import com.saferenttechnologies.android.domain.repository.InvitationRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvitationRepositoryImpl @Inject constructor(
    private val api: InvitationApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
) : InvitationRepository {

    // The token is a secret (anyone with it can open the invite) → never logged.
    override suspend fun invitation(token: String): AppResult<TenantInvitation> =
        when (val r = safeApiCall("GET invitations/token") { api.byToken(token) }) {
            is ApiResult.Success -> InvitationMapper.parse(r.data, token, System.currentTimeMillis())?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Parse())
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    override suspend fun accept(token: String): AppResult<InvitationDecision> {
        logger.d(TAG, "accept invitation")
        return decide("POST invitations/token/accept") { api.accept(token, buildJsonObject { }) }
    }

    override suspend fun reject(token: String, reason: String): AppResult<InvitationDecision> {
        logger.d(TAG, "reject invitation (reason=${reason.isNotBlank()})")
        return decide("POST invitations/token/reject") {
            api.reject(token, buildJsonObject { if (reason.isNotBlank()) put("reason", reason.trim().take(500)) })
        }
    }

    private suspend fun decide(name: String, request: suspend () -> retrofit2.Response<com.saferenttechnologies.android.data.remote.dto.ApiEnvelope<JsonElement>>) =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success -> AppResult.Success(InvitationDecision(r.message, InvitationMapper.leaseId(r.data)))
            is ApiResult.NotModified -> AppResult.Success(InvitationDecision(""))
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    private companion object {
        const val TAG = "SR-Invitation"
    }
}

/**
 * JSON → [TenantInvitation] (pure, unit-tested). Lenient about the shape:
 * `{ invitation: {…}, property: {…}, landlord: {…} }` or one flat invitation object with nested property / unit / bed.
 */
object InvitationMapper {
    fun parse(data: JsonElement?, token: String, now: Long): TenantInvitation? {
        val root = data as? JsonObject ?: return null
        val inv = root.obj("invitation") ?: root
        val prop = inv.obj("property") ?: root.obj("property")
        val landlord = inv.obj("landlord") ?: root.obj("landlord") ?: prop?.obj("landlord") ?: prop?.obj("owner")
        val unit = inv.obj("unit") ?: root.obj("unit")
        val bed = inv.obj("bed") ?: root.obj("bed")
        val room = bed?.obj("room") ?: inv.obj("room") ?: root.obj("room")

        val target = inv.text("target").ifBlank {
            listOfNotNull(
                unit?.text("unitNumber")?.takeIf { it.isNotBlank() },
                room?.text("roomNumber")?.takeIf { it.isNotBlank() }?.let { "Room $it" },
                bed?.text("bedNumber")?.takeIf { it.isNotBlank() }?.let { "Bed $it" },
            ).joinToString(" · ")
        }
        val place = prop?.text("place").orEmpty().ifBlank {
            listOf(prop?.text("locality").orEmpty(), prop?.text("city").orEmpty()).filter { it.isNotBlank() }.joinToString(", ")
        }
        val cover = prop?.text("coverImageId")?.ifBlank { null }
            ?: prop?.obj("coverImage")?.text("id")?.ifBlank { null }
            ?: prop?.arrayAt("images")?.firstNotNullOfOrNull { (it as? JsonObject)?.text("id")?.ifBlank { null } }

        val expiresAt = inv.text("expiresAt")
        val rawStatus = inv.text("status").uppercase().ifBlank { "PENDING" }
        val expired = rawStatus == "PENDING" && parseIsoMillis(expiresAt)?.let { it < now } == true

        return TenantInvitation(
            token = token,
            id = inv.text("id"),
            code = inv.text("code"),
            status = if (expired) "EXPIRED" else rawStatus,
            propertyId = inv.text("propertyId").ifBlank { prop?.text("id").orEmpty() },
            propertyTitle = prop?.text("title").orEmpty().ifBlank { inv.text("propertyTitle") },
            propertyType = prop?.text("propertyType").orEmpty(),
            place = place,
            coverImageId = cover,
            target = target,
            landlordName = landlord?.text("fullName").orEmpty().ifBlank { landlord?.text("name").orEmpty() }.ifBlank { inv.text("landlordName") },
            landlordVerified = landlord?.bool("verified") == true || landlord?.text("kycStatus").equals("VERIFIED", true),
            recipientName = inv.text("recipientName"),
            monthlyRent = inv.text("monthlyRent"),
            securityDeposit = inv.text("securityDeposit"),
            maintenanceCharges = inv.text("maintenanceCharges"),
            startDate = inv.text("startDate"),
            endDate = inv.text("endDate"),
            rentDueDay = inv.intOrNull("rentDueDay"),
            noticePeriodDays = inv.intOrNull("noticePeriodDays"),
            lockInMonths = inv.intOrNull("lockInMonths"),
            additionalTerms = inv.text("additionalTerms"),
            expiresAt = expiresAt,
        )
    }

    /** Accept may return the lease it created: `{ lease: { id } }` or `{ leaseId }`. */
    fun leaseId(data: JsonElement?): String? {
        val o = data as? JsonObject ?: return null
        return o.text("leaseId").ifBlank { o.obj("lease")?.text("id").orEmpty() }.ifBlank { null }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false
    private fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }?.toInt()
}
