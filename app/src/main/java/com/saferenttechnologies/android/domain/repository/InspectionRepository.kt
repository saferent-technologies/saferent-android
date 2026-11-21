package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Inspection
import com.saferenttechnologies.android.domain.model.InspectionForm
import com.saferenttechnologies.android.domain.model.ScheduleForm

/**
 * Inspection reports of one property. 400 INVALID_INPUT → AppError.FieldIssues with full paths
 * ("repairRequirements.0.item"); 412 → AppError.InvalidState. Never logs remarks, readings or photo urls.
 */
interface InspectionRepository {
    suspend fun list(propertyId: String): AppResult<List<Inspection>>
    suspend fun schedule(propertyId: String, form: ScheduleForm): AppResult<Inspection>
    /** Sends the cleaned form (status COMPLETED when [InspectionForm.complete]). Success = server message. */
    suspend fun record(propertyId: String, inspectionId: String, form: InspectionForm): AppResult<String>
    suspend fun acknowledge(propertyId: String, inspectionId: String): AppResult<String>
    /** One photo (JPEG from the app camera) for this inspection and area. */
    suspend fun uploadPhoto(propertyId: String, inspectionId: String, category: String, filePath: String): AppResult<Unit>

    /** Unsaved report kept on the phone (app killed while typing). */
    suspend fun draft(inspectionId: String): InspectionForm?
    suspend fun saveDraft(inspectionId: String, form: InspectionForm?)
}
