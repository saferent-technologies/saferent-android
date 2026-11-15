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

data class RequestPhoto(val id: String, val url: String, val updateId: String? = null, val byTenant: Boolean = false, val createdAt: String = "")

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

    fun descriptionOk(text: String) = text.trim().length in DESCRIPTION_MIN..DESCRIPTION_MAX
    fun canSend(type: String?, category: String?, description: String) = !type.isNullOrBlank() && !category.isNullOrBlank() && descriptionOk(description)
    fun remarkOk(text: String) = text.trim().length >= REMARK_MIN

    /** Landlord "Send update": a status change or a remark is needed. */
    fun canUpdate(current: String, newStatus: String?, remark: String) = (newStatus != null && newStatus != current) || remark.isNotBlank()
}
