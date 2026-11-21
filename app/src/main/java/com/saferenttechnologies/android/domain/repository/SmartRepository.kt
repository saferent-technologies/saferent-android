package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.InspectionComparison
import com.saferenttechnologies.android.domain.model.PropertyQr
import com.saferenttechnologies.android.domain.model.QrResolution
import com.saferenttechnologies.android.domain.model.ReminderData
import com.saferenttechnologies.android.domain.model.ReminderSettings

interface SmartRepository {
    suspend fun comparison(leaseId: String): AppResult<InspectionComparison>
    suspend fun propertyQr(propertyId: String): AppResult<PropertyQr>
    suspend fun resolveQr(code: String, unit: String?): AppResult<QrResolution>
    suspend fun reminders(): AppResult<ReminderData>
    suspend fun saveReminders(settings: ReminderSettings): AppResult<Pair<String, ReminderData>>
}
