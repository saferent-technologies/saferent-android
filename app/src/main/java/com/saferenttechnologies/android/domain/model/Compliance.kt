package com.saferenttechnologies.android.domain.model

/** Private (owner, managers, SafeRent; lease: landlord + tenant). Never on listings, never logged. */
data class OwnershipDeclaration(
    val text: String = "",
    val version: String = "v1",
    val declaredAt: String? = null,
    val declaredBy: String? = null,
    /** false → must declare (again) before submitting. */
    val current: Boolean = false,
)

/** PUT properties/{id}/compliance (whole object). Amount kept as typed text. */
data class PropertyCompliance(
    val assessmentNumber: String = "",
    /** PAID | PENDING | NOT_APPLICABLE | null. */
    val propertyTaxStatus: String? = null,
    val propertyTaxPaidYear: String? = null,
    val propertyTaxAmount: String = "",
    val buildingApprovalNumber: String = "",
    val buildingApprovalAuthority: String = "",
    val buildingApprovalDate: String? = null,
)

data class ComplianceDocument(
    val docType: String,
    val label: String = "",
    val whereApplicable: Boolean = false,
    val uploaded: Boolean = false,
    val documentId: String? = null,
    /** APPROVED | PENDING | REJECTED | null. */
    val status: String? = null,
    val expiryDate: String? = null,
    /** VALID | EXPIRING | EXPIRED | null. */
    val expiryState: String? = null,
)

data class ExpiringDocument(val documentId: String, val docType: String, val name: String, val expiryDate: String, val expiryState: String)

data class ComplianceData(
    val declaration: OwnershipDeclaration = OwnershipDeclaration(),
    val compliance: PropertyCompliance = PropertyCompliance(),
    val documents: List<ComplianceDocument> = emptyList(),
    val expiring: List<ExpiringDocument> = emptyList(),
    val updatedAt: String? = null,
)

/** REGISTERED | NOTARIZED | E_STAMP | UNREGISTERED | null. */
data class LeaseAgreement(
    val type: String? = null,
    val registrationNumber: String = "",
    val office: String = "",
    val date: String? = null,
    val stampDutyAmount: String = "",
)

/** NOT_STARTED | SUBMITTED | VERIFIED | NOT_REQUIRED. */
data class PoliceVerification(
    val status: String = "NOT_STARTED",
    val reference: String = "",
    val submittedOn: String? = null,
    val verifiedOn: String? = null,
)

data class LeaseCompliance(
    val leaseId: String = "",
    val agreement: LeaseAgreement = LeaseAgreement(),
    val police: PoliceVerification = PoliceVerification(),
    val canEditAgreement: Boolean = false,
    val canEditPolice: Boolean = false,
)

/** Client checks (the server checks again). Error keys = the API's issue paths / body fields. */
object ComplianceRules {
    const val DECLARATION_VERSION = "v1"
    val TAX_STATUSES = listOf("PAID", "PENDING", "NOT_APPLICABLE")
    val AUTHORITIES = listOf("GHMC", "BBMP", "BMC", "MCD", "GCC", "HMDA", "DTCP", "Panchayat")
    /** Upload asks "Does this document have an expiry date?" for these. */
    val EXPIRY_DOCS = setOf("FIRE_NOC", "TRADE_LICENSE", "SOCIETY_NOC")
    val AGREEMENT_TYPES = listOf("REGISTERED", "NOTARIZED", "E_STAMP", "UNREGISTERED")
    val POLICE_STATUSES = listOf("NOT_STARTED", "SUBMITTED", "VERIFIED", "NOT_REQUIRED")
    const val ASSESSMENT_MAX = 60
    const val APPROVAL_MAX = 80
    const val AUTHORITY_MAX = 120
    const val AGREEMENT_NUMBER_MAX = 80
    const val OFFICE_MAX = 120
    const val REFERENCE_MAX = 80
    const val AMOUNT_MAX = 100_000_000.0

    enum class Problem { YEAR_REQUIRED, YEAR_FORMAT, AMOUNT, APPROVAL_NUMBER, FUTURE_DATE, TOO_LONG, AGREEMENT_NUMBER, VERIFIED_DATE, VERIFIED_BEFORE }

    /** Submit 412 problem that means "declare first". */
    fun isDeclarationProblem(problem: String) = problem.contains("ownership declaration", ignoreCase = true)

    /** "2025-26": the second part is the next year. */
    fun fyOk(text: String): Boolean {
        val m = Regex("^(\\d{4})-(\\d{2})$").find(text.trim()) ?: return false
        val start = m.groupValues[1].toInt()
        return m.groupValues[2].toInt() == (start + 1) % 100 && start in 1950..2100
    }

    /** Financial years (April to March), newest first: this one and the 5 before. */
    fun financialYears(today: SimpleDate = SimpleDate.today(), count: Int = 6): List<String> {
        val current = if (today.month >= 4) today.year else today.year - 1
        return (0 until count).map { i -> val y = current - i; String.format(java.util.Locale.US, "%d-%02d", y, (y + 1) % 100) }
    }

    /** "4,200" → 4200.0; blank → null; bad → NaN. */
    fun amount(text: String): Double? {
        val t = text.replace(",", "").trim()
        if (t.isEmpty()) return null
        val v = t.toDoubleOrNull() ?: return Double.NaN
        val decimals = !t.contains('.') || t.substringAfter('.').length <= 2
        return if (v < 0 || v > AMOUNT_MAX || !decimals) Double.NaN else v
    }

    fun amountText(v: Double?): String = when {
        v == null -> ""
        v % 1.0 == 0.0 -> v.toLong().toString()
        else -> v.toString()
    }

    /** Digits and one dot, 2 decimals (while typing). */
    fun amountInput(text: String): String {
        val clean = text.filter { it.isDigit() || it == '.' }
        val dot = clean.indexOf('.')
        return if (dot < 0) clean.take(9) else clean.substring(0, dot).take(9) + "." + clean.substring(dot + 1).replace(".", "").take(2)
    }

    private fun future(date: String?, today: SimpleDate): Boolean = date?.let { SimpleDate.parse(it.take(10)) }?.let { it > today } == true

    fun validate(c: PropertyCompliance, today: SimpleDate = SimpleDate.today()): Map<String, Problem> = buildMap {
        if (c.assessmentNumber.trim().length > ASSESSMENT_MAX) put("assessmentNumber", Problem.TOO_LONG)
        if (c.propertyTaxStatus == "PAID") {
            val y = c.propertyTaxPaidYear?.trim().orEmpty()
            if (y.isEmpty()) put("propertyTaxPaidYear", Problem.YEAR_REQUIRED) else if (!fyOk(y)) put("propertyTaxPaidYear", Problem.YEAR_FORMAT)
            amount(c.propertyTaxAmount)?.let { if (it.isNaN()) put("propertyTaxAmount", Problem.AMOUNT) }
        }
        val approvalGiven = c.buildingApprovalAuthority.isNotBlank() || !c.buildingApprovalDate.isNullOrBlank()
        if (approvalGiven && c.buildingApprovalNumber.isBlank()) put("buildingApprovalNumber", Problem.APPROVAL_NUMBER)
        if (c.buildingApprovalNumber.trim().length > APPROVAL_MAX) put("buildingApprovalNumber", Problem.TOO_LONG)
        if (c.buildingApprovalAuthority.trim().length > AUTHORITY_MAX) put("buildingApprovalAuthority", Problem.TOO_LONG)
        if (future(c.buildingApprovalDate, today)) put("buildingApprovalDate", Problem.FUTURE_DATE)
    }

    /** What is sent: year / amount only with Paid. */
    fun clean(c: PropertyCompliance) = c.copy(
        assessmentNumber = c.assessmentNumber.trim(),
        propertyTaxPaidYear = c.propertyTaxPaidYear?.trim()?.ifBlank { null }.takeIf { c.propertyTaxStatus == "PAID" },
        propertyTaxAmount = if (c.propertyTaxStatus == "PAID") c.propertyTaxAmount.trim() else "",
        buildingApprovalNumber = c.buildingApprovalNumber.trim(),
        buildingApprovalAuthority = c.buildingApprovalAuthority.trim(),
    )

    fun validateLease(l: LeaseCompliance, today: SimpleDate = SimpleDate.today()): Map<String, Problem> = buildMap {
        val a = l.agreement
        if (l.canEditAgreement) {
            if ((a.type == "REGISTERED" || a.type == "E_STAMP") && a.registrationNumber.isBlank()) put("agreementRegistrationNumber", Problem.AGREEMENT_NUMBER)
            if (a.registrationNumber.trim().length > AGREEMENT_NUMBER_MAX) put("agreementRegistrationNumber", Problem.TOO_LONG)
            if (a.office.trim().length > OFFICE_MAX) put("agreementOffice", Problem.TOO_LONG)
            amount(a.stampDutyAmount)?.let { if (it.isNaN()) put("stampDutyAmount", Problem.AMOUNT) }
        }
        val p = l.police
        if (l.canEditPolice) {
            if (p.reference.trim().length > REFERENCE_MAX) put("policeVerificationRef", Problem.TOO_LONG)
            if (future(p.submittedOn, today)) put("policeVerificationSubmittedOn", Problem.FUTURE_DATE)
            if (p.status == "VERIFIED") {
                if (p.verifiedOn.isNullOrBlank()) put("policeVerificationVerifiedOn", Problem.VERIFIED_DATE)
                else if (future(p.verifiedOn, today)) put("policeVerificationVerifiedOn", Problem.FUTURE_DATE)
            }
            val s = p.submittedOn?.let { SimpleDate.parse(it.take(10)) }
            val v = p.verifiedOn?.let { SimpleDate.parse(it.take(10)) }
            if (s != null && v != null && v < s) put("policeVerificationVerifiedOn", Problem.VERIFIED_BEFORE)
        }
    }

    /**
     * PUT leases/{id}/compliance: only changed fields, only what this side may edit.
     * Values: String / Double / null (cleared). Empty map = nothing to send.
     */
    fun leaseChanges(old: LeaseCompliance, new: LeaseCompliance): Map<String, Any?> = buildMap {
        fun <T> diff(key: String, a: T, b: T, value: Any?) { if (a != b) put(key, value) }
        if (new.canEditAgreement) {
            val o = old.agreement
            val n = new.agreement
            diff("agreementType", o.type, n.type, n.type)
            diff("agreementRegistrationNumber", o.registrationNumber.trim(), n.registrationNumber.trim(), n.registrationNumber.trim().ifBlank { null })
            diff("agreementOffice", o.office.trim(), n.office.trim(), n.office.trim().ifBlank { null })
            diff("agreementDate", o.date, n.date, n.date)
            diff("stampDutyAmount", amount(o.stampDutyAmount), amount(n.stampDutyAmount), amount(n.stampDutyAmount)?.takeUnless { it.isNaN() })
        }
        if (new.canEditPolice) {
            val o = old.police
            val n = new.police
            diff("policeVerificationStatus", o.status, n.status, n.status)
            diff("policeVerificationRef", o.reference.trim(), n.reference.trim(), n.reference.trim().ifBlank { null })
            diff("policeVerificationSubmittedOn", o.submittedOn, n.submittedOn, n.submittedOn)
            diff("policeVerificationVerifiedOn", o.verifiedOn, n.verifiedOn, n.verifiedOn)
        }
    }
}
