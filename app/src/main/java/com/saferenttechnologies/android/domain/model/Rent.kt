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
    /** "Nov 2026" (tenant history). */
    val period: String = "",
    /** Tenant's "I paid" proof for this month: PENDING (waiting for the landlord) | REJECTED | null. */
    val requestStatus: String? = null,
    /** Why the landlord rejected the proof. */
    val requestNote: String? = null,
    /** Monthly rent part of [amount]. */
    val rentAmount: String = "",
    /** Monthly maintenance part of [amount] (blank / 0 = none). */
    val maintenanceAmount: String = "",
) {
    /** True when this month's bill has maintenance on top of rent, so the split is worth showing. */
    val hasMaintenance: Boolean
        get() = (maintenanceAmount.toBigDecimalOrNull()?.signum() ?: 0) > 0

    val requestPending: Boolean get() = requestStatus == "PENDING"
    val isPaid: Boolean get() = status == "PAID"
}

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

/** Tenant: the security deposit of the lease. */
data class DepositInfo(
    val amount: String = "",
    val paid: Boolean = false,
    val paidAmount: String = "",
    val paidAt: String = "",
    val receipt: RentReceipt? = null,
    /** What's left of the deposit to use as rent (null → the full paid amount). */
    val available: String? = null,
    /** Landlord ticked "deposit can be used as rent" (pricing.depositUsableAsRent). Off → no such option. */
    val usableAsRent: Boolean = false,
) {
    /** Deposit that can still pay a month (landlord approves). */
    val usable: String get() = if (!paid) "" else (available ?: paidAmount.ifBlank { amount })
    val canUseForRent: Boolean get() = usableAsRent && usable.toBigDecimalOrNull()?.let { it.signum() > 0 } == true
}

/** Tenant: deposit + rent history of one lease (GET leases/{id}/payments). */
data class TenantPayments(val deposit: DepositInfo? = null, val dues: List<RentDue> = emptyList()) {
    /** Not paid yet (due / partial / overdue), oldest first → shown on top. */
    val pending: List<RentDue> get() = dues.filter { !it.isPaid }.sortedBy { it.dueDate }
    /** Paid, newest first → below. */
    val paid: List<RentDue> get() = dues.filter { it.isPaid }.sortedByDescending { it.paidAt.ifBlank { it.dueDate } }
    val overdueCount: Int get() = pending.count { it.status == "OVERDUE" }
}

/** Tenant's proof that a month was paid outside the app (landlord approves / rejects). */
data class PaymentRequest(
    val id: String,
    val dueId: String = "",
    val period: String = "",
    val dueAmount: String = "",
    /** What the tenant says they paid. */
    val amount: String = "",
    val transactionId: String = "",
    val proofUrl: String = "",
    val tenantName: String = "",
    val propertyTitle: String = "",
    val place: String = "",
    val createdAt: String = "",
    /** PENDING | APPROVED | REJECTED */
    val status: String = "PENDING",
    /** PROOF (paid outside, with screenshot) | DEPOSIT (pay this month from the security deposit). */
    val kind: String = "PROOF",
) {
    val fromDeposit: Boolean get() = kind == "DEPOSIT"
}

/** Client checks for the "I paid" form (the server checks again). */
object PaymentProofRules {
    const val TXN_MIN = 4
    fun amountOk(amount: String): Boolean = amount.toBigDecimalOrNull()?.let { it.signum() > 0 } == true
    fun txnOk(txn: String): Boolean = txn.trim().length >= TXN_MIN
    fun canSubmit(amount: String, txn: String, hasProof: Boolean): Boolean = amountOk(amount) && txnOk(txn) && hasProof
}
