package com.saferenttechnologies.android.domain.model

/** GET leases/{id}/exit — the mutual "close lease" request state for this viewer. */
data class LeaseExitState(
    /** ACTIVE | ENDED | TERMINATED */
    val leaseStatus: String = "ACTIVE",
    /** TENANT | LANDLORD | null (viewer is neither → hide everything). */
    val side: String? = null,
    val noticePeriodDays: Int? = null,
    val endDate: String? = null,
    val canRequest: Boolean = false,
    val open: LeaseExitRequest? = null,
    val history: List<LeaseExitRequest> = emptyList(),
) {
    val isTenant: Boolean get() = side.equals("TENANT", true)
    val isClosed: Boolean get() = leaseStatus.equals("ENDED", true) || leaseStatus.equals("TERMINATED", true)

    /** The other side asked → I have to accept / decline ("Close requested" chip). */
    val needsMyResponse: Boolean get() = open?.canRespond == true && open.isPending

    /** When the lease closed (the last COMPLETED request, else the lease end date). */
    val closedOn: String? get() = history.firstOrNull { it.status.equals("COMPLETED", true) }?.completedAt ?: open?.completedAt ?: endDate
}

data class LeaseExitRequest(
    val id: String,
    val leaseId: String = "",
    val requestedByRole: String = "",
    val requestedBy: String = "",
    val reason: String = "",
    val moveOutDate: String = "",
    /** PENDING | ACCEPTED (open) · DECLINED | CANCELLED | COMPLETED (history). */
    val status: String = "PENDING",
    val respondedBy: String? = null,
    val respondedAt: String? = null,
    val responseNote: String? = null,
    val completedAt: String? = null,
    val createdAt: String = "",
    val hasMyReview: Boolean = false,
    val canRespond: Boolean = false,
    val canCancel: Boolean = false,
) {
    val isPending: Boolean get() = status.equals("PENDING", true)
    val isAccepted: Boolean get() = status.equals("ACCEPTED", true)
}

/** Lease header on the lease screen (GET leases/{id}, or the list row it was opened from). */
data class LeaseSummary(
    val id: String,
    val status: String = "",
    val propertyTitle: String = "",
    val place: String = "",
    val monthlyRent: String = "",
    val securityDeposit: String = "",
    val rentDueDay: Int? = null,
    val startDate: String = "",
    val endDate: String = "",
    /** Tenant sees the landlord, landlord sees the tenant. */
    val otherName: String = "",
    /** Full address of the rental ("Flat 302, Green Heights, Madhapur, Hyderabad 500081"). */
    val address: String = "",
    /** Other side's phone (only while the server allows it, e.g. active lease). */
    val otherPhone: String = "",
    /** When it ended (past leases). */
    val endedOn: String = "",
) {
    val isActive: Boolean get() = status.isBlank() || status in setOf("ACTIVE", "CLOSE_REQUESTED", "NOTICE")
}

/** Tenant's "My leases": the active one on top, ended ones below (newest first). */
object TenantLeases {
    fun split(all: List<LeaseSummary>): Pair<LeaseSummary?, List<LeaseSummary>> {
        val active = all.firstOrNull { it.isActive }
        val past = all.filter { it !== active && !it.isActive }.sortedByDescending { it.endedOn.ifBlank { it.endDate }.ifBlank { it.startDate } }
        return active to past
    }
}


/** Review form from GET leases/{id}/review → data.form. */
data class ReviewCategory(val key: String, val label: String, val required: Boolean = false)
data class ReviewForm(val categories: List<ReviewCategory> = emptyList(), val tags: List<String> = emptyList()) {
    /** "overall" always exists (required) even when the server sent no form. */
    val allCategories: List<ReviewCategory>
        get() = if (categories.any { it.key == OVERALL }) categories else listOf(ReviewCategory(OVERALL, "Overall", true)) + categories

    companion object {
        const val OVERALL = "overall"
    }
}

data class LeaseReviewInfo(val eligible: Boolean = false, val hasMyReview: Boolean = false, val form: ReviewForm = ReviewForm())

/** A review being written (ratings 1–5 by category key). */
data class ReviewDraft(val ratings: Map<String, Int> = emptyMap(), val text: String = "", val tags: Set<String> = emptySet()) {
    val isComplete: Boolean get() = (ratings[ReviewForm.OVERALL] ?: 0) in 1..5

    fun toggleTag(tag: String): ReviewDraft =
        if (tag in tags) copy(tags = tags - tag) else if (tags.size >= MAX_TAGS) this else copy(tags = tags + tag)

    companion object {
        const val MAX_TAGS = 8
        const val MAX_TEXT = 3000
    }
}

/** Client-side rules for the close-lease forms (the server checks them too). */
object LeaseExitRules {
    const val REASON_MIN = 10
    const val REASON_MAX = 2000
    const val DECLINE_MIN = 5
    const val NOTE_MAX = 1000
    private const val DEFAULT_NOTICE = 30

    fun defaultMoveOut(today: SimpleDate, noticeDays: Int?): SimpleDate = today.plusDays(noticeDays ?: DEFAULT_NOTICE)

    /** Earlier than today + notice → amber hint (never blocks). */
    fun isShortNotice(date: SimpleDate, today: SimpleDate, noticeDays: Int?): Boolean =
        noticeDays != null && noticeDays > 0 && date < today.plusDays(noticeDays)

    fun reasonOk(reason: String): Boolean = reason.trim().length in REASON_MIN..REASON_MAX
    fun declineOk(note: String): Boolean = note.trim().length in DECLINE_MIN..NOTE_MAX

    val TENANT_REASONS = listOf("Job transfer", "Moving to own house", "Rent too high", "Maintenance issues", "Personal reasons")
    val LANDLORD_REASONS = listOf("Selling the property", "Family moving in", "Renovation", "Rent not paid", "Personal reasons")
}

/** Tenant dashboard lease → the lease screen header (shown before GET leases/{id} answers). */
fun TenantLease.toSummary() = LeaseSummary(
    id = id,
    status = "ACTIVE",
    propertyTitle = propertyTitle,
    place = place,
    monthlyRent = monthlyRent,
    rentDueDay = rentDueDay,
    startDate = startDate,
    endDate = endDate,
    otherName = landlordName,
)

/** A closed lease I still have to review (GET reviews/pending). */
data class PendingReview(
    val leaseId: String,
    val propertyId: String = "",
    val propertyTitle: String = "",
    val place: String = "",
    /** Flat / portion / room of that lease (null = whole property). */
    val unitId: String? = null,
    /** PG bed of that lease. */
    val bedId: String? = null,
    /** Landlord sees the tenant, tenant sees the landlord. */
    val otherName: String = "",
    val endedAt: String = "",
    /** Tenant only: deposit amount of that lease (the refund question). */
    val depositAmount: String = "",
) {
    /** Ask "was the deposit refunded?" only when there was a deposit (> ₹0). */
    val asksDeposit: Boolean get() = depositAmount.toBigDecimalOrNull()?.let { it.signum() > 0 } == true
}

/** Landlord: an unreviewed past tenant blocks new invites ONLY for that same flat / bed, not the rest. */
object ReviewLocks {
    fun dueFor(reviews: List<PendingReview>, unitId: String?, bedId: String?): PendingReview? = reviews.firstOrNull { r ->
        when {
            r.bedId != null -> r.bedId == bedId
            r.unitId != null -> r.unitId == unitId
            else -> unitId == null && bedId == null // whole-property lease → whole-property invite
        }
    }
}

/** Tenant's answer to "Did your landlord refund your deposit?" */
enum class DepositRefund { YES, PARTIAL, NO }
