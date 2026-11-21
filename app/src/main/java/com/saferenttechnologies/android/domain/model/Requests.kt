package com.saferenttechnologies.android.domain.model

/** GET requests/options (cached): types with their categories, priorities, landlord statuses. */
data class RequestOptions(
    val types: List<RequestType> = emptyList(),
    val priorities: List<String> = listOf("LOW", "MEDIUM", "HIGH", "URGENT"),
    /** key → label: OPEN "Pending", IN_PROGRESS "In progress", RESOLVED "Resolved". */
    val landlordStatuses: List<Pair<String, String>> = emptyList(),
)

data class RequestType(val key: String, val label: String, val categories: List<RequestCategory> = emptyList())
data class RequestCategory(val key: String, val label: String)

/** A photo or a video (kind VIDEO / mimeType video/mp4) of the request or of an update. */
data class RequestPhoto(
    val id: String,
    val url: String,
    val updateId: String? = null,
    val byTenant: Boolean = false,
    val createdAt: String = "",
    val kind: String = "PHOTO",
    val mimeType: String = "",
) {
    val isVideo: Boolean get() = kind == "VIDEO" || mimeType.startsWith("video/")
}

/** An invoice / receipt: a photo or a PDF. */
data class RequestInvoice(val id: String, val url: String, val mimeType: String = "", val byTenant: Boolean = false, val createdAt: String = "") {
    val isPdf: Boolean get() = mimeType == "application/pdf"
}

data class RequestTechnician(val name: String, val phone: String? = null, val onSafeRent: Boolean = false)

/** One line of the timeline. */
data class RequestEvent(
    val id: String,
    /** CREATED | STATUS | REMARK | REMINDER | RESOLVED | REOPENED | CANCELLED */
    val kind: String,
    /** TENANT | LANDLORD | STAFF | PROVIDER */
    val byRole: String = "",
    val byName: String = "",
    val toStatusLabel: String = "",
    val remark: String = "",
    val photoIds: List<String> = emptyList(),
    val at: String = "",
)

/** What this viewer may do (the server decides; buttons only from here). */
data class RequestCan(
    val update: Boolean = false,
    val remind: Boolean = false,
    val resolve: Boolean = false,
    val reopen: Boolean = false,
    val cancel: Boolean = false,
    val addPhotos: Boolean = false,
    val addInvoice: Boolean = false,
)

/** A maintenance / service / complaint request (the API's Request object). */
data class ServiceRequest(
    val id: String,
    val type: String = "",
    val typeLabel: String = "",
    val category: String = "",
    val categoryLabel: String = "",
    val title: String = "",
    val description: String = "",
    val priority: String = "MEDIUM",
    val status: String = "OPEN",
    val statusLabel: String = "",
    val lastRemark: String = "",
    val propertyTitle: String = "",
    val propertyPlace: String = "",
    val place: String = "",
    val leaseId: String = "",
    val raisedByName: String = "",
    val photos: List<RequestPhoto> = emptyList(),
    val invoices: List<RequestInvoice> = emptyList(),
    val technician: RequestTechnician? = null,
    val estimatedCost: Double? = null,
    val actualCost: Double? = null,
    /** OWNER | TENANT | SHARED | NO_COST; null until resolved. */
    val paidBy: String? = null,
    val workStartedAt: String? = null,
    val completedAt: String? = null,
    val timeline: List<RequestEvent> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastReminderAt: String? = null,
    val needsAction: Boolean = false,
    val can: RequestCan = RequestCan(),
) {
    val isOpen: Boolean get() = status == "OPEN" || status == "IN_PROGRESS" || status == "ASSIGNED" || status == "ACCEPTED"
}

data class RequestPage(val items: List<ServiceRequest>, val page: Int, val totalPages: Int, val needsAction: Int = 0) {
    val hasMore: Boolean get() = page < totalPages
}

data class RequestSummary(val needsAction: Int = 0, val landlordOpen: Int = 0, val myOpen: Int = 0)

/** List tabs → the API's status filter (null = all). */
enum class RequestTab(val status: String?) {
    NEEDS_ACTION("NEEDS_ACTION"), ACTIVE("ACTIVE"), RESOLVED("RESOLVED"), ALL(null);

    companion object {
        // "All" first (and selected when the list opens).
        val TENANT = listOf(ALL, ACTIVE, RESOLVED)
        val LANDLORD = listOf(ALL, NEEDS_ACTION, ACTIVE, RESOLVED)
    }
}

/** Client checks (the server checks again). */
object RequestRules {
    const val DESCRIPTION_MIN = 10
    const val DESCRIPTION_MAX = 3000
    const val REMARK_MIN = 5
    const val MAX_PHOTOS = 10
    /** Per upload (raise / photos / reopen / update). */
    const val MAX_VIDEOS = 2
    const val VIDEO_MAX_BYTES = 40L * 1024 * 1024
    const val FILE_MAX_BYTES = 10L * 1024 * 1024
    const val COST_MAX = 10_000_000.0
    const val TECHNICIAN_MAX = 120
    const val WORK_START_DAYS = 60
    val PAID_BY = listOf("OWNER", "TENANT", "SHARED", "NO_COST")

    /** "1,500.50" / "1500" → 1500.5; blank → null; bad / out of range → NaN. */
    fun cost(text: String): Double? {
        val t = text.replace(",", "").trim()
        if (t.isEmpty()) return null
        val v = t.toDoubleOrNull() ?: return Double.NaN
        val twoDecimals = !t.contains('.') || t.substringAfter('.').length <= 2
        return if (v < 0 || v > COST_MAX || !twoDecimals) Double.NaN else v
    }

    /** Cost as the user sees it in a field ("1500", "1500.5"). */
    fun costText(v: Double?): String = when {
        v == null -> ""
        v % 1.0 == 0.0 -> v.toLong().toString()
        else -> v.toString()
    }

    enum class ResolveProblem { NO_PAYER, BAD_AMOUNT, NO_COST_AMOUNT }

    /**
     * Tenant "Mark resolved": who paid is required; the amount is optional but must be > 0
     * (No cost: blank or 0). Owner: [payerRequired] false.
     */
    fun resolveProblem(paidBy: String?, amount: String, payerRequired: Boolean = true): ResolveProblem? {
        if (paidBy == null) return if (payerRequired) ResolveProblem.NO_PAYER else if (amount.isBlank()) null else ResolveProblem.NO_PAYER
        val v = cost(amount)
        return when {
            v != null && v.isNaN() -> ResolveProblem.BAD_AMOUNT
            paidBy == "NO_COST" -> if (v != null && v > 0) ResolveProblem.NO_COST_AMOUNT else null
            v != null && v <= 0 -> ResolveProblem.BAD_AMOUNT
            else -> null
        }
    }

    /** Amount to send: No cost → none; blank → none. */
    fun resolveAmount(paidBy: String?, amount: String): Double? =
        if (paidBy == null || paidBy == "NO_COST") null else cost(amount)?.takeUnless { it.isNaN() || it <= 0 }

    /** Indian mobile / landline: 10 digits (optional +91 / 0). Blank = fine (optional). */
    fun phoneOk(text: String): Boolean {
        val d = text.filter { it.isDigit() || it == '+' }
        if (d.isEmpty()) return true
        val digits = d.removePrefix("+").let { if (it.length == 12 && it.startsWith("91")) it.drop(2) else it.removePrefix("0") }
        return digits.length == 10 && digits.all { it.isDigit() }
    }

    /** "+919876543210" for the API; blank → null. */
    fun phoneForApi(text: String): String? {
        val d = text.filter { it.isDigit() }
        if (d.isEmpty()) return null
        val ten = when {
            d.length == 12 && d.startsWith("91") -> d.drop(2)
            d.length == 11 && d.startsWith("0") -> d.drop(1)
            else -> d
        }
        return "+91$ten"
    }

    /** Work start: from 60 days back to 60 days ahead (the server checks again). */
    fun workStartOk(date: String?, today: SimpleDate = SimpleDate.today()): Boolean {
        if (date.isNullOrBlank()) return true
        val d = SimpleDate.parse(date) ?: return false
        val diff = SimpleDateDays.between(today, d)
        return diff in -WORK_START_DAYS..WORK_START_DAYS
    }

    /** Video count check for one upload. */
    fun tooManyVideos(videoCount: Int) = videoCount > MAX_VIDEOS

    fun descriptionOk(text: String) = text.trim().length in DESCRIPTION_MIN..DESCRIPTION_MAX
    fun canSend(type: String?, category: String?, description: String) = !type.isNullOrBlank() && !category.isNullOrBlank() && descriptionOk(description)
    fun remarkOk(text: String) = text.trim().length >= REMARK_MIN

    /** Landlord "Send update": a status change or a remark is needed. */
    fun canUpdate(current: String, newStatus: String?, remark: String) = (newStatus != null && newStatus != current) || remark.isNotBlank()
}

/** Whole days from [a] to [b] (calendar dates, no time zone). */
object SimpleDateDays {
    fun between(a: SimpleDate, b: SimpleDate): Int {
        fun ms(d: SimpleDate) = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(d.year, d.month - 1, d.day) }.timeInMillis
        return ((ms(b) - ms(a)) / 86_400_000L).toInt()
    }
}

/** Owner "Work details": what changed is sent (cleared fields as null). */
data class WorkDetails(
    val technicianName: String = "",
    val technicianPhone: String = "",
    val estimatedCost: String = "",
    val workStartDate: String? = null,
) {
    companion object {
        fun of(r: ServiceRequest) = WorkDetails(
            technicianName = r.technician?.name.orEmpty(),
            technicianPhone = r.technician?.phone.orEmpty(),
            estimatedCost = RequestRules.costText(r.estimatedCost),
            workStartDate = r.workStartedAt?.take(10),
        )
    }
}
