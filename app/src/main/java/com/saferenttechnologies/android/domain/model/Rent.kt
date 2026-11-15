package com.saferenttechnologies.android.domain.model

/** The three rent figures on the landlord dashboard → one list each. */
enum class RentBucket(val key: String) { EXPECTED("EXPECTED"), COLLECTED("COLLECTED"), OVERDUE("OVERDUE") }

/** One month's rent of one lease (GET rent/dues). */
data class RentDue(
    val id: String,
    val leaseId: String = "",
    val tenantName: String = "",
    val propertyId: String = "",
    val propertyTitle: String = "",
    /** "Flat 302", "Room 2 · Bed B". */
    val place: String = "",
    val amount: String = "",
    val dueDate: String = "",
    /** DUE | PAID | PARTIAL | OVERDUE */
    val status: String = "",
    val paidAmount: String = "",
    val paidAt: String = "",
    val daysOverdue: Int? = null,
    val lastReminderAt: String? = null,
    /** Server says a reminder can go now (not already sent today, lease active …). */
    val canRemind: Boolean = false,
    val receipt: RentReceipt? = null,
)

data class RentReceipt(
    val id: String,
    val number: String = "",
    val amount: String = "",
    val paidAt: String = "",
    /** UPI / CASH / BANK_TRANSFER … */
    val mode: String = "",
    val period: String = "",
)

data class RentDuesPage(val items: List<RentDue>, val total: String = "", val period: String = "")

/** One money movement on the landlord's account (GET rent/transactions). */
data class RentTransaction(
    val id: String,
    /** RENT | DEPOSIT | MAINTENANCE | REFUND | … */
    val kind: String = "",
    /** IN (tenant paid) | OUT (refund / payout). */
    val direction: String = "IN",
    val amount: String = "",
    /** SUCCESS | PENDING | FAILED */
    val status: String = "SUCCESS",
    val tenantName: String = "",
    val propertyTitle: String = "",
    val place: String = "",
    val at: String = "",
    val mode: String = "",
    val reference: String = "",
    val receipt: RentReceipt? = null,
) {
    val isIn: Boolean get() = !direction.equals("OUT", true)
    /** "2026-11" — list headers group by month. */
    val month: String get() = at.take(7)
}

data class TransactionPage(val items: List<RentTransaction>, val page: Int, val totalPages: Int) {
    val hasMore: Boolean get() = page < totalPages
}
