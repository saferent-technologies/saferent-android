package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.EmergencyInfo
import com.saferenttechnologies.android.domain.model.Safety
import com.saferenttechnologies.android.domain.model.SafetyData

interface SafetyRepository {
    suspend fun safety(propertyId: String): AppResult<SafetyData>

    /** The whole object; success = server message + saved data. */
    suspend fun saveSafety(propertyId: String, safety: Safety): AppResult<Pair<String, SafetyData>>

    /** Fresh from the server; a success is cached (encrypted) for offline use, LEASE_ENDED clears it. */
    suspend fun emergency(leaseId: String): AppResult<EmergencyInfo>

    /** The last saved Emergency details of this lease (offline), or null. */
    suspend fun cachedEmergency(leaseId: String): EmergencyInfo?

    /** Logout / lease ended. */
    fun clearEmergency()
}
