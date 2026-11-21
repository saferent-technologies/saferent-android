package com.saferenttechnologies.android.data.push

/**
 * What one FCM message turns into (pure Kotlin, unit-tested).
 * title / body: message.notification first, then data["title"] / data["body"].
 */
data class PushPayload(
    val notificationId: String,
    val title: String,
    val body: String,
    val link: String,
    val type: String,
    /** Picture to show big in the notification (announcement image …); blank = none. */
    val imageUrl: String = "",
) {
    /** Same id for the same server notification → a repeat replaces instead of stacking. */
    val androidId: Int get() = if (notificationId.isNotBlank()) notificationId.hashCode() else (title + body).hashCode()

    /** PendingIntent extras read by MainActivity. */
    val extras: Map<String, String>
        get() = buildMap {
            if (link.isNotBlank()) put(EXTRA_LINK, link)
            if (notificationId.isNotBlank()) put(EXTRA_NOTIFICATION_ID, notificationId)
            if (type.isNotBlank()) put(EXTRA_TYPE, type)
        }

    companion object {
        const val EXTRA_LINK = "link"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_TYPE = "type"

        /** DOCUMENT_EXPIRY (propertyId) → Compliance; LEASE_COMPLIANCE / SOCIETY_APPROVAL (leaseId) → that lease. */
        fun complianceLink(data: Map<String, String>): String? = when (data[EXTRA_TYPE]?.uppercase()) {
            "DOCUMENT_EXPIRY" -> data["propertyId"]?.takeIf { it.isNotBlank() }?.let { "/properties/$it/compliance" } ?: "/properties"
            // Smart rent reminder (data.rentPaymentId) → the tenant's rent due (Pay / Send proof).
            "RENT_REMINDER" -> "/rent"
            "LEASE_COMPLIANCE", "SOCIETY_APPROVAL" -> data["leaseId"]?.takeIf { it.isNotBlank() }?.let { "/leases/$it" } ?: "/leases"
            else -> null
        }

        /** SAFETY_EXPIRY (data.propertyId) → that property's Safety form. */
        fun safetyLink(data: Map<String, String>): String? {
            if (data[EXTRA_TYPE]?.uppercase() != "SAFETY_EXPIRY") return null
            val id = data["propertyId"]?.takeIf { it.isNotBlank() } ?: return "/properties"
            return "/properties/$id/safety"
        }

        /** VISIT / VISIT_FEEDBACK pushes (data.visitId) → "/visits/{id}" (+ ?feedback=1). */
        fun visitLink(data: Map<String, String>): String? {
            val type = data[EXTRA_TYPE]?.uppercase()
            if (type != "VISIT" && type != "VISIT_FEEDBACK") return null
            val id = data["visitId"]?.takeIf { it.isNotBlank() } ?: return "/visits"
            return "/visits/$id" + if (type == "VISIT_FEEDBACK") "?feedback=1" else ""
        }

        /** null = nothing to show (no title and no body). [fallback] = our own text for old status pushes. */
        fun from(
            notificationTitle: String?,
            notificationBody: String?,
            data: Map<String, String>,
            fallback: Pair<String, String>? = null,
            notificationImage: String? = null,
        ): PushPayload? {
            val title = notificationTitle?.takeIf { it.isNotBlank() } ?: data["title"]?.takeIf { it.isNotBlank() } ?: fallback?.first
            val body = notificationBody?.takeIf { it.isNotBlank() } ?: data["body"]?.takeIf { it.isNotBlank() }
                ?: data["message"]?.takeIf { it.isNotBlank() } ?: fallback?.second
            if (title.isNullOrBlank() && body.isNullOrBlank()) return null
            return PushPayload(
                notificationId = data[EXTRA_NOTIFICATION_ID].orEmpty(),
                title = title.orEmpty(),
                body = body.orEmpty(),
                link = visitLink(data) ?: safetyLink(data) ?: complianceLink(data) ?: com.saferenttechnologies.android.domain.model.LeaseLinks.withLeaseId(
                    data[EXTRA_LINK].orEmpty(),
                    data["leaseId"] ?: data["lease_id"],
                ),
                type = data[EXTRA_TYPE].orEmpty().ifBlank { "GENERAL" },
                imageUrl = listOf(data["imageUrl"], data["image"], notificationImage).firstOrNull { !it.isNullOrBlank() }.orEmpty(),
            )
        }
    }
}
