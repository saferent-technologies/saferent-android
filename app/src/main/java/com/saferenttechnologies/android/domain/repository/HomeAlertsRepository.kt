package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.HomeAlert

interface HomeAlertsRepository {
    suspend fun get(): AppResult<HomeAlert>
    suspend fun save(alert: HomeAlert): AppResult<HomeAlert>
}
