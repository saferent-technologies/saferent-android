package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ComplianceData
import com.saferenttechnologies.android.domain.model.LeaseCompliance
import com.saferenttechnologies.android.domain.model.PropertyCompliance

interface ComplianceRepository {
    suspend fun compliance(propertyId: String): AppResult<ComplianceData>

    /** Whole object (not the declaration). Success = message + saved data. */
    suspend fun saveCompliance(propertyId: String, compliance: PropertyCompliance): AppResult<Pair<String, ComplianceData>>

    /** Owner only (403 for managers). */
    suspend fun declare(propertyId: String, version: String): AppResult<Pair<String, ComplianceData>>

    suspend fun leaseCompliance(leaseId: String): AppResult<LeaseCompliance>

    /** Only changed fields ([ComplianceRules.leaseChanges]). */
    suspend fun saveLeaseCompliance(leaseId: String, changes: Map<String, Any?>): AppResult<Pair<String, LeaseCompliance>>
}
