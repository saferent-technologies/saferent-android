package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.LookupQuery
import com.saferenttechnologies.android.domain.model.PersonPreview
import com.saferenttechnologies.android.domain.model.TenantReport

interface PeopleRepository {
    /** Success(null) = not on SafeRent yet (404). */
    suspend fun lookup(query: LookupQuery): AppResult<PersonPreview?>
    suspend fun tenantReport(query: LookupQuery): AppResult<TenantReport?>
}
