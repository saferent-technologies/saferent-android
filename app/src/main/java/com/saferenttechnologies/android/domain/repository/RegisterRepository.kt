package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Register
import com.saferenttechnologies.android.domain.model.RegisterItem
import com.saferenttechnologies.android.domain.model.RegisterItemForm
import com.saferenttechnologies.android.domain.model.RepairForm

/**
 * Furniture & appliances register. 400 → AppError.FieldIssues (full paths), 412 → InvalidState.
 * Results that change an item return the server's copy (always redraw from it). Never logs contents.
 */
interface RegisterRepository {
    /** [draft] = the wizard's change request copy (view=draft). */
    suspend fun register(propertyId: String, draft: Boolean = false, unitId: String? = null, roomId: String? = null): AppResult<Register>
    suspend fun create(propertyId: String, form: RegisterItemForm, draft: Boolean = false): AppResult<Pair<String, RegisterItem?>>
    /** Only the changed fields. */
    suspend fun update(propertyId: String, item: RegisterItem, form: RegisterItemForm, draft: Boolean = false): AppResult<Pair<String, RegisterItem?>>
    suspend fun delete(propertyId: String, itemId: String): AppResult<String>
    /** Photos from the app camera (≤ 2000 px JPEG), sent one per call. */
    suspend fun uploadPhoto(propertyId: String, itemId: String, filePath: String): AppResult<String>
    suspend fun deletePhoto(propertyId: String, itemId: String, photoId: String): AppResult<Unit>
    suspend fun addRepair(propertyId: String, itemId: String, form: RepairForm): AppResult<RegisterItem?>
    suspend fun markRepairDone(propertyId: String, itemId: String, repairId: String): AppResult<RegisterItem?>
    suspend fun deleteRepair(propertyId: String, itemId: String, repairId: String): AppResult<Unit>
}
