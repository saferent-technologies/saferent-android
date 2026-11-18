package com.saferenttechnologies.android.domain.model

/** Help & support: tickets raised to SafeRent (not to the landlord — that's Requests). */
data class TicketPerson(val id: String = "", val fullName: String = "", val role: String = "")

data class TicketMessage(
    val id: String,
    val authorId: String = "",
    val body: String = "",
    val createdAt: String = "",
    val author: TicketPerson? = null,
) {
    /** From SafeRent (support / admin) rather than me. */
    val fromSupport: Boolean get() = SupportRules.isSupportRole(author?.role)
}

data class Ticket(
    val id: String,
    val category: String = "",
    val subject: String = "",
    val description: String = "",
    val priority: String = "MEDIUM",
    val status: String = "OPEN",
    val createdAt: String = "",
    val updatedAt: String = "",
    val resolvedAt: String? = null,
    val propertyId: String? = null,
    val propertyTitle: String = "",
    val raisedBy: TicketPerson? = null,
    val assignedTo: TicketPerson? = null,
    /** Staff member who opened the ticket (null until someone from SafeRent opens it). */
    val connectedTo: TicketPerson? = null,
    val connectedAt: String? = null,
    val messageCount: Int = 0,
    val messages: List<TicketMessage> = emptyList(),
) {
    val closed: Boolean get() = status.equals("CLOSED", true)
}

data class TicketPage(val items: List<Ticket>, val page: Int, val totalPages: Int) {
    val hasMore: Boolean get() = page < totalPages
}

/** List tabs → status filters ("Open" = OPEN + IN_PROGRESS, merged). */
enum class SupportTab(val statuses: List<String>) {
    OPEN(listOf("OPEN", "IN_PROGRESS")),
    RESOLVED(listOf("RESOLVED")),
    CLOSED(listOf("CLOSED")),
}

object SupportRules {
    const val SUBJECT_MIN = 5
    const val SUBJECT_MAX = 150
    const val DESCRIPTION_MIN = 10
    const val DESCRIPTION_MAX = 5000
    const val MESSAGE_MAX = 5000
    val PRIORITIES = listOf("LOW", "MEDIUM", "HIGH", "URGENT")
    private val SUPPORT_ROLES = setOf("SUPPORT", "ADMIN", "SUPER_ADMIN")

    /**
     * "Connected to" only once a staff member has really opened the ticket: the server's connectedTo,
     * else whoever from SafeRent already replied (they opened it). Just being assigned is NOT connected.
     */
    fun agentName(t: Ticket): String? =
        t.connectedTo?.fullName?.trim()?.ifBlank { null }
            ?: t.messages.lastOrNull { it.fromSupport }?.author?.fullName?.trim()?.ifBlank { null }

    /** "Ravi Kumar" → "RK", "ravi" → "R". */
    fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "S" }

    /** Tenant complaint only for landlords; landlord complaint only for tenants. */
    fun categories(landlord: Boolean): List<String> =
        listOf("PAYMENT", if (landlord) "TENANT_COMPLAINT" else "LANDLORD_COMPLAINT", "ACCOUNT", "PROPERTY", "OTHER")

    fun subjectOk(s: String) = s.trim().length in SUBJECT_MIN..SUBJECT_MAX
    fun descriptionOk(s: String) = s.trim().length in DESCRIPTION_MIN..DESCRIPTION_MAX
    fun canSend(category: String?, subject: String, description: String) = category != null && subjectOk(subject) && descriptionOk(description)
    fun messageOk(s: String) = s.trim().length in 1..MESSAGE_MAX

    fun isSupportRole(role: String?) = role?.uppercase() in SUPPORT_ROLES

    /** "Open" tab = two requests merged, newest activity first, no duplicates. */
    fun merge(vararg lists: List<Ticket>): List<Ticket> =
        lists.toList().flatten().distinctBy { it.id }.sortedByDescending { it.updatedAt.ifBlank { it.createdAt } }
}
