package com.saferenttechnologies.android.domain.model

data class SocietyRule(val title: String = "", val description: String = "")
data class CommonArea(val name: String = "", val timings: String = "", val notes: String = "")

/** GET / PUT properties/{id}/society "society". Amounts kept as typed text; maintenanceMonthly is the server's (read-only). */
data class Society(
    val isSociety: Boolean = false,
    val associationName: String = "",
    val registrationNumber: String = "",
    val contactName: String = "",
    /** SECRETARY | PRESIDENT | MANAGER | FACILITY_MANAGER | OTHER | null. */
    val contactRole: String? = null,
    val contactPhone: String = "",
    val contactEmail: String = "",
    val officeHours: String = "",
    val maintenanceAmount: String = "",
    /** MONTHLY | QUARTERLY | HALF_YEARLY | YEARLY | null. */
    val maintenanceFrequency: String? = null,
    val maintenanceMonthly: Double? = null,
    /** INCLUDED_IN_RENT | TENANT | OWNER | null. */
    val maintenancePaidBy: String? = null,
    /** SOCIETY | OWNER (only when the tenant pays). */
    val maintenancePayTo: String? = null,
    val rules: List<SocietyRule> = emptyList(),
    val visitorPolicy: String = "",
    val gateAccessMethods: List<String> = emptyList(),
    val gateContactName: String = "",
    val gateContactPhone: String = "",
    val clubhouseRules: String = "",
    val commonAreas: List<CommonArea> = emptyList(),
    val moveRequiresPermission: Boolean? = null,
    val moveNoticeDays: Int? = null,
    val moveAllowedTimings: String = "",
    val moveChargeAmount: String = "",
    val moveDepositAmount: String = "",
    val moveLiftBooking: Boolean? = null,
    val moveNotes: String = "",
    val updatedAt: String? = null,
)

/** vehicleType CAR | BIKE | BOTH. */
data class ParkingSlot(
    val id: String = "",
    val slotNumber: String = "",
    val unitId: String? = null,
    val unitLabel: String? = null,
    val vehicleType: String = "CAR",
    val covered: Boolean = false,
    val notes: String = "",
)

data class RulesDocument(val documentId: String, val sharedWithTenant: Boolean = false)

data class SocietyData(
    val society: Society? = null,
    val parkingSlots: List<ParkingSlot> = emptyList(),
    val rulesDocument: RulesDocument? = null,
)

data class ListingMove(
    val requiresPermission: Boolean? = null,
    val noticeDays: Int? = null,
    val allowedTimings: String = "",
    val charge: Double? = null,
    val deposit: Double? = null,
    val liftBooking: Boolean? = null,
    val notes: String = "",
) {
    val isEmpty: Boolean get() = requiresPermission == null && noticeDays == null && allowedTimings.isBlank() && charge == null && deposit == null && liftBooking == null && notes.isBlank()
}

data class ParkingSummary(val vehicleType: String, val covered: Boolean, val count: Int)

/** Browse: no phone numbers, emails or contact names. */
data class ListingSociety(
    val associationName: String = "",
    val maintenanceAmount: Double? = null,
    val maintenanceFrequency: String? = null,
    val maintenanceMonthly: Double? = null,
    val maintenancePaidBy: String? = null,
    val rules: List<SocietyRule> = emptyList(),
    val visitorPolicy: String = "",
    val gateAccessMethods: List<String> = emptyList(),
    val clubhouseRules: String = "",
    val commonAreas: List<CommonArea> = emptyList(),
    val move: ListingMove? = null,
    val parking: List<ParkingSummary> = emptyList(),
)

/** status NOT_REQUIRED | PENDING | APPROVED | REJECTED | null. */
data class SocietyApproval(val status: String? = null, val date: String? = null, val note: String = "")

data class LeaseSociety(
    val society: Society? = null,
    val myParking: List<ParkingSlot> = emptyList(),
    val rulesDocument: RulesDocument? = null,
    val approval: SocietyApproval = SocietyApproval(),
    val canEditApproval: Boolean = false,
)

/** Client checks (the server checks again). Keys = the API's issue paths. */
object SocietyRules {
    val ROLES = listOf("SECRETARY", "PRESIDENT", "MANAGER", "FACILITY_MANAGER", "OTHER")
    val FREQUENCIES = listOf("MONTHLY", "QUARTERLY", "HALF_YEARLY", "YEARLY")
    val PAID_BY = listOf("INCLUDED_IN_RENT", "TENANT", "OWNER")
    val PAY_TO = listOf("SOCIETY", "OWNER")
    val GATE_METHODS = listOf("MYGATE", "NOBROKERHOOD", "ADDA", "APNACOMPLEX", "ACCESS_CARD", "BIOMETRIC", "GUARD_REGISTER", "OTHER")
    val VEHICLES = listOf("CAR", "BIKE", "BOTH")
    val APPROVALS = listOf("PENDING", "APPROVED", "REJECTED", "NOT_REQUIRED")
    /** Quick chips for common areas. */
    val AREA_SUGGESTIONS = listOf("Gym", "Swimming pool", "Terrace", "Kids play area", "Clubhouse", "Party hall")
    const val NAME_MAX = 150
    const val REG_MAX = 60
    const val MAX_RULES = 30
    const val RULE_TITLE_MAX = 80
    const val RULE_DESC_MAX = 500
    const val TEXT_MAX = 1000
    const val MAX_AREAS = 15
    const val SLOT_MAX = 20

    enum class Problem { TOO_LONG, NAME_REQUIRED, PHONE, EMAIL, AMOUNT, FREQUENCY, PAY_TO, RULE_TITLE, AREA_NAME, GATE_NAME, SLOT_NUMBER, DATE_REQUIRED, NOTE_REQUIRED, NOTICE_DAYS }

    /** Land isn't a society (the server answers 412). */
    fun applies(propertyType: String?): Boolean {
        val t = propertyType?.uppercase().orEmpty()
        return !(t.contains("LAND") || t.contains("PLOT"))
    }

    fun months(frequency: String?): Int? = when (frequency) {
        "MONTHLY" -> 1
        "QUARTERLY" -> 3
        "HALF_YEARLY" -> 6
        "YEARLY" -> 12
        else -> null
    }

    /** "≈ per month" preview while typing (the server sends maintenanceMonthly after saving). */
    fun monthly(amount: Double?, frequency: String?): Double? {
        val m = months(frequency) ?: return null
        if (amount == null || amount.isNaN()) return null
        return Math.round(amount / m * 100) / 100.0
    }

    fun emailOk(text: String) = text.isBlank() || Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(text.trim())

    private fun amountBad(text: String) = ComplianceRules.amount(text)?.isNaN() == true

    fun validate(s: Society): Map<String, Problem> = buildMap {
        if (!s.isSociety) return@buildMap
        if (s.associationName.trim().length > NAME_MAX) put("associationName", Problem.TOO_LONG)
        if (s.registrationNumber.trim().length > REG_MAX) put("registrationNumber", Problem.TOO_LONG)
        if (!SafetyRules.phoneOk(s.contactPhone)) put("contactPhone", Problem.PHONE)
        if (s.contactPhone.isNotBlank() && s.contactName.isBlank()) put("contactName", Problem.NAME_REQUIRED)
        if (!emailOk(s.contactEmail)) put("contactEmail", Problem.EMAIL)
        if (amountBad(s.maintenanceAmount)) put("maintenanceAmount", Problem.AMOUNT)
        if (s.maintenanceAmount.isNotBlank() && s.maintenanceFrequency == null) put("maintenanceFrequency", Problem.FREQUENCY)
        if (s.maintenancePaidBy == "TENANT" && s.maintenancePayTo == null) put("maintenancePayTo", Problem.PAY_TO)
        if (s.rules.size > MAX_RULES) put("rules", Problem.TOO_LONG)
        s.rules.forEachIndexed { i, r ->
            if (r.title.isBlank() && r.description.isBlank()) return@forEachIndexed
            if (r.title.trim().length !in 2..RULE_TITLE_MAX) put("rules.$i.title", Problem.RULE_TITLE)
            if (r.description.trim().length > RULE_DESC_MAX) put("rules.$i.description", Problem.TOO_LONG)
        }
        if (s.visitorPolicy.trim().length > TEXT_MAX) put("visitorPolicy", Problem.TOO_LONG)
        if (s.clubhouseRules.trim().length > TEXT_MAX) put("clubhouseRules", Problem.TOO_LONG)
        if (!SafetyRules.phoneOk(s.gateContactPhone)) put("gateContactPhone", Problem.PHONE)
        if (s.gateContactPhone.isNotBlank() && s.gateContactName.isBlank()) put("gateContactName", Problem.GATE_NAME)
        if (s.commonAreas.size > MAX_AREAS) put("commonAreas", Problem.TOO_LONG)
        s.commonAreas.forEachIndexed { i, a ->
            if (a.name.isBlank() && (a.timings.isNotBlank() || a.notes.isNotBlank())) put("commonAreas.$i.name", Problem.AREA_NAME)
        }
        if (s.moveRequiresPermission == true) {
            if (amountBad(s.moveChargeAmount)) put("moveChargeAmount", Problem.AMOUNT)
            if (amountBad(s.moveDepositAmount)) put("moveDepositAmount", Problem.AMOUNT)
            s.moveNoticeDays?.let { if (it !in 0..90) put("moveNoticeDays", Problem.NOTICE_DAYS) }
        }
    }

    /** What is sent: dependent answers only with their parent; empty rows dropped; isSociety=false → only that. */
    fun clean(s: Society): Society {
        if (!s.isSociety) return Society(isSociety = false)
        val permission = s.moveRequiresPermission == true
        return s.copy(
            associationName = s.associationName.trim(),
            registrationNumber = s.registrationNumber.trim(),
            contactName = s.contactName.trim(),
            contactEmail = s.contactEmail.trim(),
            officeHours = s.officeHours.trim(),
            maintenanceAmount = s.maintenanceAmount.trim(),
            maintenanceMonthly = null,
            maintenancePayTo = s.maintenancePayTo.takeIf { s.maintenancePaidBy == "TENANT" },
            rules = s.rules.map { SocietyRule(it.title.trim(), it.description.trim()) }.filter { it.title.isNotEmpty() || it.description.isNotEmpty() },
            visitorPolicy = s.visitorPolicy.trim(),
            gateAccessMethods = s.gateAccessMethods.distinct(),
            gateContactName = s.gateContactName.trim(),
            clubhouseRules = s.clubhouseRules.trim(),
            commonAreas = s.commonAreas.map { CommonArea(it.name.trim(), it.timings.trim(), it.notes.trim()) }.filter { it.name.isNotEmpty() || it.timings.isNotEmpty() || it.notes.isNotEmpty() },
            moveNoticeDays = s.moveNoticeDays.takeIf { permission },
            moveChargeAmount = if (permission) s.moveChargeAmount.trim() else "",
            moveDepositAmount = if (permission) s.moveDepositAmount.trim() else "",
            moveAllowedTimings = s.moveAllowedTimings.trim(),
            moveNotes = s.moveNotes.trim(),
            updatedAt = null,
        )
    }

    fun validateSlot(slot: ParkingSlot): Map<String, Problem> = buildMap {
        if (slot.slotNumber.isBlank()) put("slotNumber", Problem.SLOT_NUMBER)
        if (slot.slotNumber.trim().length > SLOT_MAX) put("slotNumber", Problem.TOO_LONG)
        if (slot.notes.trim().length > 200) put("notes", Problem.TOO_LONG)
    }

    /** PATCH body for a slot: only changed fields (null frees the slot / clears notes). */
    fun slotChanges(old: ParkingSlot, new: ParkingSlot): Map<String, Any?> = buildMap {
        if (old.slotNumber.trim() != new.slotNumber.trim()) put("slotNumber", new.slotNumber.trim())
        if (old.unitId != new.unitId) put("unitId", new.unitId)
        if (old.vehicleType != new.vehicleType) put("vehicleType", new.vehicleType)
        if (old.covered != new.covered) put("covered", new.covered)
        if (old.notes.trim() != new.notes.trim()) put("notes", new.notes.trim().ifBlank { null })
    }

    /** Allocated first (by flat), then unallocated; slot numbers in natural order inside. */
    fun grouped(slots: List<ParkingSlot>): List<Pair<String?, List<ParkingSlot>>> {
        val byUnit = slots.sortedBy { it.slotNumber.lowercase() }.groupBy { it.unitId }
        val allocated = byUnit.filterKeys { it != null }.entries
            .sortedBy { (_, list) -> list.first().unitLabel.orEmpty().lowercase() }
            .map { (k, v) -> k to v }
        return allocated + listOfNotNull(byUnit[null]?.let { null to it })
    }

    fun validateApproval(a: SocietyApproval): Map<String, Problem> = buildMap {
        if ((a.status == "APPROVED" || a.status == "REJECTED") && a.date.isNullOrBlank()) put("date", Problem.DATE_REQUIRED)
        if (a.status == "REJECTED" && a.note.isBlank()) put("note", Problem.NOTE_REQUIRED)
        if (a.note.trim().length > 500) put("note", Problem.TOO_LONG)
    }
}
