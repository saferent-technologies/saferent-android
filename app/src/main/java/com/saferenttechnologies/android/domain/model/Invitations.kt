package com.saferenttechnologies.android.domain.model

import java.math.BigDecimal

/** A landlord's rental invitation, as the invited tenant sees it (GET invitations/token/{token}). */
data class TenantInvitation(
    val token: String,
    val id: String = "",
    val code: String = "",
    /** PENDING / ACCEPTED / REJECTED / REVOKED / EXPIRED (a PENDING one past [expiresAt] reads as EXPIRED). */
    val status: String = "PENDING",
    val propertyId: String = "",
    val propertyTitle: String = "",
    val propertyType: String = "",
    val place: String = "",
    val coverImageId: String? = null,
    /** "Flat 302", "Room 2 · Bed B" … */
    val target: String = "",
    val landlordName: String = "",
    val landlordVerified: Boolean = false,
    val recipientName: String = "",
    val monthlyRent: String = "",
    val securityDeposit: String = "",
    val maintenanceCharges: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val rentDueDay: Int? = null,
    val noticePeriodDays: Int? = null,
    val lockInMonths: Int? = null,
    val additionalTerms: String = "",
    val expiresAt: String = "",
    /** What the owner asks when accepting; null = nothing (old accept flow). */
    val requirements: InviteRequirements? = null,
    /** Requested documents: label + which vault types count. */
    val documentOptions: List<DocumentOption> = emptyList(),
    val houseRules: HouseRules = HouseRules(),
    /** The tenant's own last answers (never references). */
    val prefill: TenantAnswers? = null,
) {
    val isPending: Boolean get() = status.equals("PENDING", true)

    /** Rent + deposit (+ maintenance) due at move-in; blank when the rent is unknown. */
    val moveInTotal: String
        get() {
            val rent = monthlyRent.toBigDecimalOrNull() ?: return ""
            val sum = listOf(securityDeposit, maintenanceCharges).mapNotNull { it.toBigDecimalOrNull() }.fold(rent, BigDecimal::add)
            return sum.toPlainString()
        }

    companion object {
        val CLOSED = listOf("ACCEPTED", "REJECTED", "REVOKED", "EXPIRED")
    }
}

/** What happened after Accept / Decline: server message + the lease it created (if any) + documents to share now. */
data class InvitationDecision(
    val message: String,
    val leaseId: String? = null,
    val documentsToShare: List<DocumentOption> = emptyList(),
)

// ---------------- what the owner asks (invitation requirements) ----------------

/** Owner's "Ask the tenant". companyName: OFF | OPTIONAL | REQUIRED; references 0–2; documents max 4. */
data class InviteRequirements(
    val askDetails: Boolean = true,
    val companyName: String = "OFF",
    val references: Int = 0,
    val backgroundVerification: Boolean = false,
    val documents: List<String> = emptyList(),
) {
    /** Everything off = ask nothing (the field is left out). */
    val asksAnything: Boolean get() = askDetails || companyName != "OFF" || references > 0 || backgroundVerification || documents.isNotEmpty()
}

data class DocumentOption(val key: String, val label: String = "", val vaultTypes: List<String> = emptyList())

/** pets / smoking: YES | NO | NOT_APPLICABLE | NOT_DISCLOSED. */
data class HouseRules(val petsAllowed: String = "", val smokingAllowed: String = "", val lockInMonths: Int? = null)

data class TenantReference(val name: String = "", val relation: String = "", val phone: String = "")

/** Tenant's answers when accepting (numbers as typed; null = not answered yet). Kept in memory only. */
data class TenantAnswers(
    val tenantType: String = "",
    val employmentType: String = "",
    val companyName: String = "",
    val adults: String = "1",
    val children: String = "0",
    val hasPets: Boolean? = null,
    val petDetails: String = "",
    val smokes: Boolean? = null,
    val stayMonths: String = "",
    val references: List<TenantReference> = emptyList(),
    val backgroundConsent: Boolean = false,
)

/** Owner: GET invitations/{id}/tenant-details. */
data class TenantDetailsView(
    val requirements: InviteRequirements? = null,
    val details: AnsweredDetails? = null,
    val documents: List<DocShareState> = emptyList(),
    val conflicts: List<String> = emptyList(),
)

data class AnsweredDetails(
    val tenantType: String = "",
    val employmentType: String = "",
    /** "" when not asked or the lease is not active. */
    val companyName: String = "",
    val adults: Int? = null,
    val children: Int? = null,
    val hasPets: Boolean? = null,
    val petDetails: String = "",
    val smokes: Boolean? = null,
    val stayMonths: Int? = null,
    val references: List<TenantReference> = emptyList(),
    val backgroundConsentAt: String = "",
    val leaseActive: Boolean = true,
    val answeredAt: String = "",
)

data class DocShareState(val key: String, val label: String, val shared: Boolean)

/** Local checks for the tenant's answers (pure, unit-tested). Keys = the server's issue paths. */
object InviteAnswerRules {
    val TENANT_TYPES = listOf("STUDENT", "WORKING_PROFESSIONAL", "FAMILY", "OTHER")
    val EMPLOYMENT_TYPES = listOf("SALARIED", "SELF_EMPLOYED", "STUDENT", "NOT_WORKING", "OTHER")
    val DOCUMENTS = listOf("ID_PROOF", "PAN", "EMPLOYMENT_OR_STUDENT_ID", "OTHER")
    const val MAX_DOCUMENTS = 4
    private val PHONE = Regex("^\\+?[1-9]\\d{9,14}$")

    fun phoneOk(raw: String): Boolean = PHONE.matches(raw.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' })

    /** A required company name isn't needed for students and people not working. */
    fun companyRequired(r: InviteRequirements, a: TenantAnswers): Boolean =
        r.companyName == "REQUIRED" && a.employmentType !in setOf("STUDENT", "NOT_WORKING")

    /** Pre-filled answers with exactly [InviteRequirements.references] reference cards. */
    fun start(r: InviteRequirements, prefill: TenantAnswers?): TenantAnswers {
        val base = (prefill ?: TenantAnswers()).copy(references = emptyList(), backgroundConsent = false)
        return base.copy(references = List(r.references.coerceIn(0, 2)) { TenantReference() })
    }

    enum class Problem { REQUIRED, RANGE, LENGTH, PHONE, CONSENT }

    fun validate(r: InviteRequirements, a: TenantAnswers): Map<String, Problem> = buildMap {
        if (r.askDetails) {
            if (a.tenantType !in TENANT_TYPES) put("details.tenantType", Problem.REQUIRED)
            if (a.employmentType !in EMPLOYMENT_TYPES) put("details.employmentType", Problem.REQUIRED)
            val adults = a.adults.toIntOrNull()
            if (adults == null || adults !in 1..20) put("details.adults", Problem.RANGE)
            val children = a.children.ifBlank { "0" }.toIntOrNull()
            if (children == null || children !in 0..20) put("details.children", Problem.RANGE)
            if (a.hasPets == null) put("details.hasPets", Problem.REQUIRED)
            if (a.hasPets == true && a.petDetails.trim().length > 120) put("details.petDetails", Problem.LENGTH)
            if (a.smokes == null) put("details.smokes", Problem.REQUIRED)
            if (a.stayMonths.isNotBlank()) {
                val m = a.stayMonths.toIntOrNull()
                if (m == null || m !in 1..120) put("details.stayMonths", Problem.RANGE)
            }
        }
        if (r.companyName != "OFF") {
            val c = a.companyName.trim()
            if (c.length > 120) put("details.companyName", Problem.LENGTH)
            else if (c.isEmpty() && companyRequired(r, a)) put("details.companyName", Problem.REQUIRED)
        }
        a.references.take(r.references).forEachIndexed { i, ref ->
            if (ref.name.trim().length !in 2..120) put("details.references.$i.name", Problem.LENGTH)
            if (ref.relation.trim().length !in 2..40) put("details.references.$i.relation", Problem.LENGTH)
            if (!phoneOk(ref.phone)) put("details.references.$i.phone", Problem.PHONE)
        }
        if (r.backgroundVerification && !a.backgroundConsent) put("details.backgroundConsent", Problem.CONSENT)
    }

    enum class Warning { PETS, SMOKING, LOCK_IN }

    /** Not blocking: answers that clash with the house rules. */
    fun warnings(r: InviteRequirements, a: TenantAnswers, rules: HouseRules): List<Warning> = buildList {
        if (!r.askDetails) return@buildList
        if (a.hasPets == true && rules.petsAllowed.equals("NO", true)) add(Warning.PETS)
        if (a.smokes == true && rules.smokingAllowed.equals("NO", true)) add(Warning.SMOKING)
        val stay = a.stayMonths.toIntOrNull()
        if (stay != null && rules.lockInMonths != null && stay < rules.lockInMonths) add(Warning.LOCK_IN)
    }

    /** Values for the accept body "details" (only the asked parts; never extra fields). Pure: String / Int / Boolean / null / list. */
    fun details(r: InviteRequirements, a: TenantAnswers): Map<String, Any?> = buildMap {
        if (r.askDetails) {
            put("tenantType", a.tenantType)
            put("employmentType", a.employmentType)
            put("adults", a.adults.toIntOrNull() ?: 1)
            put("children", a.children.ifBlank { "0" }.toIntOrNull() ?: 0)
            put("hasPets", a.hasPets == true)
            put("petDetails", a.petDetails.trim().takeIf { a.hasPets == true && it.isNotEmpty() })
            put("smokes", a.smokes == true)
            put("stayMonths", a.stayMonths.toIntOrNull())
        }
        if (r.companyName != "OFF") put("companyName", a.companyName.trim().ifEmpty { null })
        if (r.references > 0) put("references", a.references.take(r.references).map { TenantReference(it.name.trim(), it.relation.trim(), it.phone.filterNot { c -> c == ' ' || c == '-' || c == '(' || c == ')' }) })
        if (r.backgroundVerification) put("backgroundConsent", a.backgroundConsent)
    }
}
