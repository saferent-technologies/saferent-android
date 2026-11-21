package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.BookVisit
import com.saferenttechnologies.android.domain.model.Visit
import com.saferenttechnologies.android.domain.model.VisitSettings
import com.saferenttechnologies.android.domain.model.VisitSettingsData
import com.saferenttechnologies.android.domain.model.VisitSlots

/** Visit booking. Results carrying a visit return (server message, visit). 400 → FieldIssues, 412 → InvalidState. */
interface VisitRepository {
    suspend fun settings(propertyId: String): AppResult<VisitSettingsData>
    suspend fun saveSettings(propertyId: String, settings: VisitSettings): AppResult<Pair<String, VisitSettingsData>>
    /** [from] "YYYY-MM-DD" (null = today), [days] 1–31. */
    suspend fun slots(propertyId: String, from: String?, days: Int): AppResult<VisitSlots>
    suspend fun book(propertyId: String, booking: BookVisit): AppResult<Pair<String, Visit?>>
    suspend fun myVisits(upcoming: Boolean): AppResult<List<Visit>>
    suspend fun cancelMine(visitId: String, reason: String): AppResult<Pair<String, Visit?>>
    suspend fun feedback(visitId: String, rating: Int, interested: Boolean, comment: String): AppResult<Pair<String, Visit?>>
    /** [propertyId] null = all my properties. */
    suspend fun hostVisits(propertyId: String?): AppResult<List<Visit>>
    /** confirm | reject (reason) | cancel (reason) | complete (outcome) | no-show. */
    suspend fun hostAction(visitId: String, action: String, reason: String? = null, outcome: String? = null): AppResult<Pair<String, Visit?>>
}
