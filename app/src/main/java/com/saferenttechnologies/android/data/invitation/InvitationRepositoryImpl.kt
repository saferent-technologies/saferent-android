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

    override suspend fun mine(): AppResult<List<TenantInvitation>> =
        when (val r = safeApiCall("GET invitations/mine") { api.mine() }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                val items = (r.data as? kotlinx.serialization.json.JsonArray)
                    ?: ((r.data as? JsonObject)?.let { it["items"] ?: it["invitations"] } as? kotlinx.serialization.json.JsonArray)
                AppResult.Success(
                    items.orEmpty().mapNotNull { e ->
                        val token = ((e as? JsonObject)?.get("token") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        InvitationMapper.parse(e, token, now)
                    }.filter { it.status.equals("PENDING", true) },
                )
            }
            is ApiResult.NotModified -> AppResult.Success(emptyList())
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    // Answers are private: never logged.
    override suspend fun accept(
        token: String,
        answers: com.saferenttechnologies.android.domain.model.TenantAnswers?,
        requirements: com.saferenttechnologies.android.domain.model.InviteRequirements?,
    ): AppResult<InvitationDecision> {
        logger.d(TAG, "accept invitation (with details=${answers != null && requirements != null})")
        val body = if (answers != null && requirements != null) InvitationMapper.acceptBody(requirements, answers) else buildJsonObject { }
        return decide("POST invitations/token/accept") { api.accept(token, body) }
    }

    override suspend fun reject(token: String, reason: String): AppResult<InvitationDecision> {
        logger.d(TAG, "reject invitation (reason=${reason.isNotBlank()})")
        return decide("POST invitations/token/reject") {
            api.reject(token, buildJsonObject { if (reason.isNotBlank()) put("reason", reason.trim().take(500)) })
        }
    }

    private suspend fun decide(name: String, request: suspend () -> retrofit2.Response<com.saferenttechnologies.android.data.remote.dto.ApiEnvelope<JsonElement>>) =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success -> AppResult.Success(InvitationDecision(r.message, InvitationMapper.leaseId(r.data), InvitationMapper.documentOptions((r.data as? JsonObject)?.get("documentsToShare"))))
            is ApiResult.NotModified -> AppResult.Success(InvitationDecision(""))
            // 400 → field messages by path ("details.companyName"); other codes as they are (409 / 410 reload).
            is ApiResult.Failure -> AppResult.Failure(
                r.error.let { e -> if (e is AppError.Server && e.httpCode == 400) com.saferenttechnologies.android.data.owner.OwnerMapper.error(e) else e },
            )
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
            requirements = requirements(inv["requirements"] ?: root["requirements"]),
            documentOptions = documentOptions(inv["documentOptions"] ?: root["documentOptions"]),
            houseRules = (root.obj("houseRules") ?: inv.obj("houseRules"))?.let { h ->
                com.saferenttechnologies.android.domain.model.HouseRules(h.text("petsAllowed").uppercase(), h.text("smokingAllowed").uppercase(), h.intOrNull("lockInMonths"))
            } ?: com.saferenttechnologies.android.domain.model.HouseRules(lockInMonths = inv.intOrNull("lockInMonths")),
            prefill = (root.obj("prefill") ?: inv.obj("prefill"))?.let { p ->
                com.saferenttechnologies.android.domain.model.TenantAnswers(
                    tenantType = p.text("tenantType").uppercase(),
                    employmentType = p.text("employmentType").uppercase(),
                    companyName = p.text("companyName"),
                    adults = p.intOrNull("adults")?.toString() ?: "1",
                    children = p.intOrNull("children")?.toString() ?: "0",
                    hasPets = (p["hasPets"] as? JsonPrimitive)?.booleanOrNull,
                    petDetails = p.text("petDetails"),
                    smokes = (p["smokes"] as? JsonPrimitive)?.booleanOrNull,
                    stayMonths = p.intOrNull("stayMonths")?.toString().orEmpty(),
                )
            },
        )
    }

    /** null / not an object / everything off → null (no form). */
    fun requirements(e: JsonElement?): com.saferenttechnologies.android.domain.model.InviteRequirements? {
        val o = e as? JsonObject ?: return null
        val r = com.saferenttechnologies.android.domain.model.InviteRequirements(
            askDetails = (o["askDetails"] as? JsonPrimitive)?.booleanOrNull ?: true,
            companyName = o.text("companyName").uppercase().takeIf { it in setOf("OFF", "OPTIONAL", "REQUIRED") } ?: "OFF",
            references = (o.intOrNull("references") ?: 0).coerceIn(0, 2),
            backgroundVerification = o.bool("backgroundVerification"),
            documents = (o["documents"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.uppercase() }.orEmpty(),
        )
        return r.takeIf { it.asksAnything }
    }

    fun documentOptions(e: JsonElement?): List<com.saferenttechnologies.android.domain.model.DocumentOption> =
        (e as? kotlinx.serialization.json.JsonArray)?.mapNotNull { x ->
            val o = x as? JsonObject ?: return@mapNotNull null
            val key = o.text("key").ifBlank { return@mapNotNull null }
            com.saferenttechnologies.android.domain.model.DocumentOption(
                key, o.text("label"),
                (o["vaultTypes"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.uppercase() }.orEmpty(),
            )
        }.orEmpty()

    /** { details: { only the asked parts } }. */
    fun acceptBody(r: com.saferenttechnologies.android.domain.model.InviteRequirements, a: com.saferenttechnologies.android.domain.model.TenantAnswers): JsonObject = buildJsonObject {
        put(
            "details",
            buildJsonObject {
                com.saferenttechnologies.android.domain.model.InviteAnswerRules.details(r, a).forEach { (k, v) ->
                    when (v) {
                        null -> put(k, kotlinx.serialization.json.JsonNull)
                        is Boolean -> put(k, v)
                        is Int -> put(k, v)
                        is List<*> -> put(
                            k,
                            kotlinx.serialization.json.buildJsonArray {
                                v.filterIsInstance<com.saferenttechnologies.android.domain.model.TenantReference>().forEach { ref ->
                                    add(buildJsonObject { put("name", ref.name); put("relation", ref.relation); put("phone", ref.phone) })
                                }
                            },
                        )
                        else -> put(k, v.toString())
                    }
                }
            },
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
