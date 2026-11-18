package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.DocumentVault
import com.saferenttechnologies.android.domain.model.SharedDocuments

interface DocumentsRepository {
    suspend fun vault(): AppResult<DocumentVault>
    /** Photos ([pages], JPEG) or one [pdf]. */
    suspend fun upload(type: String, pages: List<ByteArray>, pdf: ByteArray? = null): AppResult<DocumentVault>
    /** A document file's bytes (PDF to show in the app); null = failed. Never saved to disk here. */
    suspend fun fileBytes(url: String): ByteArray?
    suspend fun delete(id: String): AppResult<DocumentVault>
    suspend fun share(id: String, leaseId: String): AppResult<DocumentVault>
    suspend fun unshare(id: String, leaseId: String): AppResult<DocumentVault>
    suspend fun shared(leaseId: String): AppResult<SharedDocuments>
}

/** Picked PDF → bytes (checks it really is a PDF and the size). */
interface DocumentFiles {
    fun readPdf(uri: String): PdfRead
    /** File name shown under "PDF" (e.g. "aadhaar.pdf"); blank = unknown. */
    fun displayName(uri: String): String
}

sealed interface PdfRead {
    data class Ok(val bytes: ByteArray) : PdfRead
    data object TooBig : PdfRead
    data object NotPdf : PdfRead
}
