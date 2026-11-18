package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Ticket
import com.saferenttechnologies.android.domain.model.TicketPage

interface SupportRepository {
    suspend fun create(category: String, subject: String, description: String, priority: String, propertyId: String?): AppResult<Ticket>
    suspend fun list(status: String, page: Int): AppResult<TicketPage>
    suspend fun detail(id: String): AppResult<Ticket>
    suspend fun reply(id: String, body: String): AppResult<Unit>
}
