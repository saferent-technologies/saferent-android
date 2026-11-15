package com.saferenttechnologies.android.domain.model

/** Who a landlord's announcement goes to. */
enum class AnnouncementAudience { ALL, PROPERTIES, TENANTS }

/** A landlord → tenants announcement (title, text, optional image). */
data class Announcement(
    val id: String,
    val title: String = "",
    val body: String = "",
    /** Relative / absolute image url, blank = no image. */
    val imageUrl: String = "",
    val audience: AnnouncementAudience = AnnouncementAudience.ALL,
    /** "All tenants", "Green Heights, Sunrise PG", "3 tenants" — server text. */
    val audienceLabel: String = "",
    val createdAt: String = "",
    /** Landlord: how many tenants got it / opened it. */
    val recipientCount: Int = 0,
    val viewCount: Int = 0,
    /** Tenant: who sent it. */
    val fromName: String = "",
    /** Tenant: already opened. */
    val viewed: Boolean = false,
)

data class AnnouncementPage(val items: List<Announcement>, val page: Int, val totalPages: Int) {
    val hasMore: Boolean get() = page < totalPages
}

/** One tenant on "who saw it". */
data class AnnouncementViewer(
    val leaseId: String = "",
    val name: String = "",
    /** "Green Heights · Flat 302". */
    val place: String = "",
    /** Blank = not opened yet. */
    val viewedAt: String = "",
) {
    val seen: Boolean get() = viewedAt.isNotBlank()
}

/** A place the landlord can pick (from active leases): property or tenant. */
data class AnnouncementTarget(val id: String, val title: String, val subtitle: String = "")

object AnnouncementRules {
    const val TITLE_MIN = 3
    const val TITLE_MAX = 100
    const val BODY_MIN = 10
    const val BODY_MAX = 2000

    fun titleOk(t: String) = t.trim().length in TITLE_MIN..TITLE_MAX
    fun bodyOk(b: String) = b.trim().length in BODY_MIN..BODY_MAX

    /** Send is on only when the text is fine and the picked audience isn't empty. */
    fun canSend(title: String, body: String, audience: AnnouncementAudience, propertyIds: Set<String>, leaseIds: Set<String>): Boolean =
        titleOk(title) && bodyOk(body) && when (audience) {
            AnnouncementAudience.ALL -> true
            AnnouncementAudience.PROPERTIES -> propertyIds.isNotEmpty()
            AnnouncementAudience.TENANTS -> leaseIds.isNotEmpty()
        }

    /** Pickers come from the landlord's active leases: one row per property, one per tenant. */
    fun properties(leases: List<Lease>): List<AnnouncementTarget> =
        leases.filter { it.propertyId.isNotBlank() }.groupBy { it.propertyId }.map { (id, ls) ->
            AnnouncementTarget(id, ls.first().propertyTitle.ifBlank { id }, ls.size.toString())
        }.sortedBy { it.title.lowercase() }

    fun tenants(leases: List<Lease>): List<AnnouncementTarget> =
        leases.map { l ->
            AnnouncementTarget(l.id, l.tenantName.ifBlank { l.place }, listOf(l.propertyTitle, l.place).filter { it.isNotBlank() }.joinToString(" · "))
        }.sortedBy { it.title.lowercase() }

    fun search(items: List<AnnouncementTarget>, query: String): List<AnnouncementTarget> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { q in it.title.lowercase() || q in it.subtitle.lowercase() }
    }
}
