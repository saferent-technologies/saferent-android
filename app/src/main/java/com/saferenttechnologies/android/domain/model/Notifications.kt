package com.saferenttechnologies.android.domain.model

/** One in-app notification (GET notifications). */
data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val link: String = "",
    val readAt: String? = null,
    /** createdAt as epoch millis (null when the server sent something unreadable). */
    val createdAtMillis: Long? = null,
) {
    val isUnread: Boolean get() = readAt.isNullOrBlank()
}

/** One page of notifications + the user's total unread count (meta.unread). */
data class NotificationPage(
    val items: List<AppNotification>,
    val page: Int,
    val totalPages: Int,
    val unread: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}

/** Where a notification link opens. Screens the app doesn't have yet are still routed (shown as "not in the app yet"). */
sealed interface NavRoute {
    /** Landlord property screen; [tab] = overview / units / tenants / invitations / applications / documents … */
    data class PropertyDetail(val propertyId: String, val tab: String? = null) : NavRoute
    data object MyProperties : NavRoute
    data class ListingDetail(val listingId: String, val unitId: String? = null) : NavRoute
    data object Explore : NavRoute
    data class LeaseDetail(val leaseId: String) : NavRoute
    data object Leases : NavRoute
    /** Tenant: my applications; landlord: applications of their properties. */
    data object Applications : NavRoute
    data class Invitation(val token: String) : NavRoute
    data object Rent : NavRoute
    data object OwnerVerification : NavRoute
    data object Reviews : NavRoute
    data object Wallet : NavRoute
    data object Maintenance : NavRoute
    data class SupportTicket(val ticketId: String?) : NavRoute
    data object Referrals : NavRoute
    data object MyDocuments : NavRoute
    data object Notifications : NavRoute
    data object Home : NavRoute

    /** Has a screen in the app today. */
    val available: Boolean
        get() = this is PropertyDetail || this is ListingDetail || this == MyProperties || this == Explore ||
            this == Applications || this == Notifications || this == Home || this is Invitation ||
            this is LeaseDetail || this == Leases
}

/**
 * Pure link → route mapping:
 * "/properties/8f1c?tab=documents" or "/properties/8f1c/documents" → PropertyDetail("8f1c", "documents"),
 * "/applications?propertyId=8f1c" → that property's Applications tab, "/homes/{id}" (share link) → listing …
 */
object DeepLinkRouter {
    /**
     * [hint] = the notification's title / body / type. When the link opens a property without a tab
     * ("/properties/8f1c"), it picks the related tab: "New application" → Applications, etc.
     */
    fun route(link: String?, hint: String?): NavRoute {
        val r = route(link)
        return if (r is NavRoute.PropertyDetail && r.tab == null) r.copy(tab = tabFor(hint)) else r
    }

    /** Words in the notification → property tab (null = overview). Order matters: most specific first. */
    fun tabFor(hint: String?): String? {
        val t = hint?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            // Listing status / verification news → the overview (even if the text mentions tenants).
            listOf("verified", "approved", "rejected", "your changes", "publish", "suspend", "under verification").any { it in t } &&
                listOf("application", "document", "invitation").none { it in t } -> null
            "application" in t || "applied" in t -> "applications"
            "invitation" in t || "invite" in t -> "invitations"
            "move-out" in t || "move out" in t || "exit" in t || "lease" in t || "tenant" in t || "rent " in t || "rent paid" in t -> "tenants"
            "document" in t || "ownership" in t || "deed" in t -> "documents"
            "bed" in t || "unit" in t || "flat" in t || "available" in t || "occupied" in t -> "units"
            else -> null
        }
    }

    /** Notification links: unknown path → Notifications list. */
    fun route(link: String?): NavRoute {
        val raw = link?.trim().orEmpty()
        if (raw.isEmpty()) return NavRoute.Notifications
        return routeOrNull(raw) ?: NavRoute.Notifications
    }

    /** Web / app links: unknown path → null (just open the app home). */
    fun routeOrNull(link: String?): NavRoute? {
        val raw = link?.trim().orEmpty()
        if (raw.isEmpty()) return null
        // Accept full URLs too: keep only the path + query.
        val noScheme = if (raw.contains("://")) "/" + raw.substringAfter("://").substringAfter('/', "") else raw
        val path = noScheme.substringBefore('?').substringBefore('#').trim('/')
        val query = noScheme.substringAfter('?', "").substringBefore('#').split('&')
            .mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 && it[1].isNotBlank() }?.let { it[0] to it[1] } }
            .toMap()
        val parts = path.split('/').filter { it.isNotBlank() }
        val first = parts.firstOrNull()?.lowercase() ?: return NavRoute.Home
        val second = parts.getOrNull(1)
        val third = parts.getOrNull(2)
        return when (first) {
            "properties", "property", "my-properties" ->
                second?.let { NavRoute.PropertyDetail(it, query["tab"] ?: third?.lowercase()) } ?: NavRoute.MyProperties
            "browse", "homes", "listings" -> second?.let { NavRoute.ListingDetail(it, query["unitId"]) } ?: NavRoute.Explore
            "explore" -> NavRoute.Explore
            "leases", "lease" -> second?.let { NavRoute.LeaseDetail(it) } ?: NavRoute.Leases
            "applications" -> query["propertyId"]?.let { NavRoute.PropertyDetail(it, "applications") } ?: NavRoute.Applications
            "invite", "invitations" -> second?.let { NavRoute.Invitation(it) }
            "rent", "payments" -> NavRoute.Rent
            "verify", "kyc" -> NavRoute.OwnerVerification
            "reviews" -> NavRoute.Reviews
            "wallet" -> NavRoute.Wallet
            "maintenance" -> NavRoute.Maintenance
            "support" -> NavRoute.SupportTicket(second)
            "referrals" -> NavRoute.Referrals
            "my-documents" -> NavRoute.MyDocuments
            "notifications" -> NavRoute.Notifications
            "dashboard", "home" -> NavRoute.Home
            else -> null
        }
    }
}

/** "5 min ago", "Yesterday", then the date — kept pure so it can be unit-tested. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val n: Int) : RelativeTime
    data class Hours(val n: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class Date(val millis: Long) : RelativeTime

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE

        /** [startOfToday] = local midnight today (millis). */
        fun of(created: Long, now: Long, startOfToday: Long): RelativeTime {
            val diff = (now - created).coerceAtLeast(0)
            return when {
                diff < MINUTE -> JustNow
                diff < HOUR -> Minutes((diff / MINUTE).toInt())
                created >= startOfToday -> Hours((diff / HOUR).toInt())
                created >= startOfToday - 24 * HOUR -> Yesterday
                else -> Date(created)
            }
        }
    }
}

/** Incoming VIEW intents → an app link for [DeepLinkRouter] (pure, unit-tested). */
object DeepLinks {
    val WEB_HOSTS = setOf("saferenttechnologies.com", "dev.saferenttechnologies.com", "app.saferenttechnologies.com")
    const val APP_SCHEME = "saferent"

    /** A link opened by someone of the wrong role can't open its screen (→ "isn't available for your account"). */
    fun allowedFor(route: NavRoute, isTenant: Boolean): Boolean = when (route) {
        is NavRoute.PropertyDetail, NavRoute.MyProperties, NavRoute.OwnerVerification -> !isTenant
        NavRoute.Explore, is NavRoute.Invitation -> isTenant
        else -> true
    }

    /**
     * https://dev.saferenttechnologies.com/homes/abc?unitId=u1 → "/homes/abc?unitId=u1";
     * saferent://homes/abc → "/homes/abc". Anything else (other hosts, no VIEW) → null.
     */
    fun linkOf(action: String?, scheme: String?, host: String?, path: String?, query: String?): String? {
        if (action != "android.intent.action.VIEW") return null
        val p = when {
            scheme.equals("https", true) && host?.lowercase()?.let { it in WEB_HOSTS } == true -> path.orEmpty()
            scheme.equals(APP_SCHEME, true) -> "/" + listOfNotNull(host, path?.trim('/')).filter { it.isNotBlank() }.joinToString("/")
            else -> return null
        }
        val clean = "/" + p.trim('/')
        return if (query.isNullOrBlank()) clean else "$clean?$query"
    }
}
