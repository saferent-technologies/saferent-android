package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.NotificationPage

/** GET notifications, POST notifications/read. */
interface NotificationRepository {
    suspend fun list(page: Int, unreadOnly: Boolean): AppResult<NotificationPage>

    /** POST notifications/read { id }. */
    suspend fun markRead(id: String): AppResult<Unit>

    /** POST notifications/read { } → everything read. */
    suspend fun markAllRead(): AppResult<Unit>
}
