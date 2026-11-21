package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.OwnerDetails
import com.saferenttechnologies.android.domain.model.Ownership
import com.saferenttechnologies.android.domain.model.OwnershipInfo

/**
 * Landlord "Owner details" + per property "Ownership". Private data: never logged, never shown to tenants.
 * 400 INVALID_INPUT → AppError.FieldIssues with the FULL issue paths ("representative.phone", "coOwners.1.sharePercent").
 */
interface OwnerRepository {
    /** 403 PERMISSION_DENIED (not a landlord) → AppError.Server(403). */
    suspend fun ownerDetails(): AppResult<OwnerDetails>

    /** Only the changed fields (null clears). Success = server message ("Saved"). */
    suspend fun saveOwnerDetails(changes: Map<String, String?>): AppResult<Pair<String, OwnerDetails?>>

    suspend fun ownership(propertyId: String): AppResult<OwnershipInfo>

    /** The whole object every time. Success = server message + saved ownership. */
    suspend fun saveOwnership(propertyId: String, ownership: Ownership): AppResult<Pair<String, Ownership?>>

    /** POST properties/{id}/documents, docType POWER_OF_ATTORNEY. [uri] = content:// (JPG, PNG or PDF) or a local file path. */
    suspend fun uploadPowerOfAttorney(propertyId: String, uri: String): AppResult<String> = uploadPropertyDocument(propertyId, uri, "POWER_OF_ATTORNEY")

    /** POST properties/{id}/documents with any [docType] (e.g. FIRE_NOC) and an optional [expiryDate]. Success = server message. */
    suspend fun uploadPropertyDocument(propertyId: String, uri: String, docType: String, expiryDate: String? = null): AppResult<String>
}
