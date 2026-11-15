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

        /** null = nothing to show (no title and no body). [fallback] = our own text for old status pushes. */
        fun from(
            notificationTitle: String?,
            notificationBody: String?,
            data: Map<String, String>,
            fallback: Pair<String, String>? = null,
        ): PushPayload? {
            val title = notificationTitle?.takeIf { it.isNotBlank() } ?: data["title"]?.takeIf { it.isNotBlank() } ?: fallback?.first
            val body = notificationBody?.takeIf { it.isNotBlank() } ?: data["body"]?.takeIf { it.isNotBlank() }
                ?: data["message"]?.takeIf { it.isNotBlank() } ?: fallback?.second
            if (title.isNullOrBlank() && body.isNullOrBlank()) return null
            return PushPayload(
                notificationId = data[EXTRA_NOTIFICATION_ID].orEmpty(),
                title = title.orEmpty(),
                body = body.orEmpty(),
                link = com.saferenttechnologies.android.domain.model.LeaseLinks.withLeaseId(
                    data[EXTRA_LINK].orEmpty(),
                    data["leaseId"] ?: data["lease_id"],
                ),
                type = data[EXTRA_TYPE].orEmpty().ifBlank { "GENERAL" },
            )
        }
    }
}
