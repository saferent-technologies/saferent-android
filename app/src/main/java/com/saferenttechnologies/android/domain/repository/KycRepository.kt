package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.DigiLockerResult
import com.saferenttechnologies.android.domain.model.DigiLockerSession
import com.saferenttechnologies.android.domain.model.KycStatus

interface KycRepository {
    suspend fun status(): AppResult<KycStatus>
    suspend fun startDigiLocker(): AppResult<DigiLockerSession>
    suspend fun completeDigiLocker(sessionId: String, success: Boolean?, errMessage: String?): AppResult<DigiLockerResult>
    suspend fun uploadSelfie(filePath: String): AppResult<String>
    /** Owner ID document (Aadhaar XML / e-Aadhaar PDF / PAN / passport) → server checks it. */
    suspend fun uploadIdentity(form: com.saferenttechnologies.android.domain.model.IdForm): AppResult<String>
    suspend fun submit(): AppResult<String>

    /** The DigiLocker session waiting to be finished (survives process death). */
    suspend fun pendingSession(): String?
    suspend fun savePendingSession(sessionId: String?)
    /** The verification screen was already shown after this sign in (finished or skipped). Cleared on sign out. */
    suspend fun promptDone(): Boolean
    suspend fun setPromptDone(done: Boolean)
    /** POST kyc/prompt-done: the user finished or skipped the screen (server keeps it). */
    suspend fun markPromptDoneOnServer(): AppResult<String>
    /** GET kyc/tutorials: how to verify guides (one per ID type). */
    suspend fun tutorials(): AppResult<List<com.saferenttechnologies.android.domain.model.KycTutorial>>
    /** Downloads a guide PDF (public https file) to the cache → local path. */
    suspend fun downloadTutorialPdf(url: String): AppResult<String>
}
