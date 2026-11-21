package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.LeaseSociety
import com.saferenttechnologies.android.domain.model.ParkingSlot
import com.saferenttechnologies.android.domain.model.Society
import com.saferenttechnologies.android.domain.model.SocietyApproval
import com.saferenttechnologies.android.domain.model.SocietyData

interface SocietyRepository {
    suspend fun society(propertyId: String): AppResult<SocietyData>

    /** Whole object (no maintenanceMonthly / updatedAt). Success = message + saved data. */
    suspend fun saveSociety(propertyId: String, society: Society): AppResult<Pair<String, SocietyData>>

    suspend fun addSlot(propertyId: String, slot: ParkingSlot): AppResult<ParkingSlot>

    /** Only changed fields ([com.saferenttechnologies.android.domain.model.SocietyRules.slotChanges]). */
    suspend fun updateSlot(propertyId: String, slotId: String, changes: Map<String, Any?>): AppResult<ParkingSlot>

    suspend fun deleteSlot(propertyId: String, slotId: String): AppResult<Unit>

    /** Rules PDF "Share with tenants". */
    suspend fun shareDocument(documentId: String, shared: Boolean): AppResult<String>

    suspend fun leaseSociety(leaseId: String): AppResult<LeaseSociety>

    suspend fun saveApproval(leaseId: String, approval: SocietyApproval): AppResult<Pair<String, LeaseSociety>>
}
