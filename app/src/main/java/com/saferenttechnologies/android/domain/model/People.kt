package com.saferenttechnologies.android.domain.model

/** Someone found by SafeRent ID or phone (never the full number: the server sends it masked). */
data class PersonPreview(
    val id: String,
    val fullName: String = "",
    val safeRentId: String = "",
    val phoneMasked: String = "",
    val city: String = "",
    val verified: Boolean = false,
    val memberSince: String = "",
)

/** Landlord's "Review tenant": what earlier landlords said + the tenant's rent record. */
data class TenantReport(
    val person: PersonPreview,
    val rating: Double? = null,
    val reviewCount: Int = 0,
    val reviews: List<TenantReviewItem> = emptyList(),
    val leases: List<PastLeaseItem> = emptyList(),
    val payments: PaymentStats = PaymentStats(),
    val recentPayments: List<PaymentRow> = emptyList(),
)

data class TenantReviewItem(
    val rating: Double? = null,
    val comment: String = "",
    val tags: List<String> = emptyList(),
    val landlordName: String = "",
    val place: String = "",
    val createdAt: String = "",
)

data class PastLeaseItem(
    val propertyTitle: String = "",
    val city: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "",
    val monthlyRent: String = "",
)

data class PaymentStats(val total: Int = 0, val onTime: Int = 0, val late: Int = 0, val missed: Int = 0) {
    /** Paid on time out of all due months (null when there is no history). */
    val onTimePercent: Int? get() = if (total <= 0) null else (onTime * 100 / total)
}

data class PaymentRow(val period: String = "", val amount: String = "", val status: String = "", val paidOn: String = "")

/** What the landlord typed: a 10-digit Indian mobile, or a SafeRent ID. */
sealed interface LookupQuery {
    val value: String
    data class Phone(override val value: String) : LookupQuery
    data class SafeRentId(override val value: String) : LookupQuery
}

object PeopleLookup {
    /**
     * What the "Phone number or SafeRent ID" box keeps while typing: no letters → a phone number,
     * digits only, at most 10 (an 11th digit isn't taken); with letters → a SafeRent ID (24 max).
     */
    fun cleanInput(input: String): String {
        if (input.any { it.isLetter() }) return input.take(24)
        val digits = input.filter { it.isDigit() }
        // Pasted "+91 98765 43210" → the 10 digit number, not "9198765432".
        val local = if (input.trimStart().startsWith("+91") && digits.length > 10) digits.drop(2) else digits
        return local.take(10)
    }

    private val PHONE = Regex("^[6-9]\\d{9}$")
    private val ID = Regex("^[A-Z0-9][A-Z0-9-]{3,23}$")

    /** "+91 98765 43210" / "098765-43210" → Phone("9876543210"); "sr-t-0012" → SafeRentId("SR-T-0012"); else null. */
    fun parse(input: String): LookupQuery? {
        val raw = input.trim()
        if (raw.isEmpty()) return null
        val digits = raw.filter { it.isDigit() }
        val looksPhone = raw.all { it.isDigit() || it in " +-()" }
        if (looksPhone) {
            val local = when {
                digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
                digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
                else -> digits
            }
            return if (PHONE.matches(local)) LookupQuery.Phone(local) else null
        }
        val id = raw.uppercase().replace(" ", "")
        return if (ID.matches(id) && id.any { it.isLetter() }) LookupQuery.SafeRentId(id) else null
    }
}
