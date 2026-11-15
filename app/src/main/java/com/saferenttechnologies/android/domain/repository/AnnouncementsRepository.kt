package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.Announcement
import com.saferenttechnologies.android.domain.model.AnnouncementAudience
import com.saferenttechnologies.android.domain.model.AnnouncementPage
import com.saferenttechnologies.android.domain.model.AnnouncementViewer
import com.saferenttechnologies.android.domain.model.AppResult

interface AnnouncementsRepository {
    suspend fun send(
        title: String,
        body: String,
        audience: AnnouncementAudience,
        propertyIds: Set<String>,
        leaseIds: Set<String>,
        image: ByteArray?,
    ): AppResult<Announcement>

    suspend fun list(landlord: Boolean, page: Int): AppResult<AnnouncementPage>
    suspend fun detail(id: String): AppResult<Announcement>
    suspend fun recipients(id: String): AppResult<List<AnnouncementViewer>>
    /** Tenant opened it → server records the view. */
    suspend fun markViewed(id: String): AppResult<Unit>
}
