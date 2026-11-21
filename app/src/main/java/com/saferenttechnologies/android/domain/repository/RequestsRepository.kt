package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RequestOptions
import com.saferenttechnologies.android.domain.model.RequestPage
import com.saferenttechnologies.android.domain.model.RequestSummary
import com.saferenttechnologies.android.domain.model.ServiceRequest

/** Server message + the request as it is now (every action returns it). */
data class RequestResult(val message: String, val request: ServiceRequest?)

/** A file ready to upload (photo as JPEG, video as MP4 on disk, PDF bytes). */
sealed interface UploadFile {
    class Jpeg(val bytes: ByteArray) : UploadFile
    class Mp4(val path: String) : UploadFile
    class Pdf(val bytes: ByteArray) : UploadFile
}

/** Picked / taken uris → upload files (photos shrunk, videos to 720p). */
interface RequestMedia {
    sealed interface Result {
        data class Ok(val files: List<UploadFile>) : Result
        data class Failed(val problem: Problem) : Result
    }
    enum class Problem { UNREADABLE, VIDEO_TOO_BIG, FILE_TOO_BIG, TOO_MANY_VIDEOS, NOT_A_VIDEO }

    fun isVideo(uri: String): Boolean
    fun isPdf(uri: String): Boolean
    /** [onProgress] 0..100 while a video is being made smaller. */
    suspend fun prepare(uris: List<String>, onProgress: (Int) -> Unit = {}): Result
    /** Temp videos made by [prepare] / the recorder: delete after upload. */
    fun cleanUp(files: List<UploadFile>)
}

/** Owner / manager update. Work-detail keys are sent only when [sendWork]; blanks clear them. */
data class RequestUpdate(
    val status: String? = null,
    val remark: String = "",
    val sendWork: Boolean = false,
    val technicianName: String? = null,
    val technicianPhone: String? = null,
    val estimatedCost: Double? = null,
    val workStartDate: String? = null,
    /** Only with status RESOLVED. */
    val actualCost: Double? = null,
    val paidBy: String? = null,
)

interface RequestsRepository {
    /** Cached after the first success. */
    suspend fun options(): AppResult<RequestOptions>
    suspend fun create(type: String, category: String, description: String, priority: String, leaseId: String?, files: List<UploadFile>, onProgress: (Int) -> Unit = {}): AppResult<RequestResult>
    suspend fun list(status: String?, landlord: Boolean, page: Int): AppResult<RequestPage>
    suspend fun summary(): AppResult<RequestSummary>
    suspend fun detail(id: String): AppResult<ServiceRequest>
    suspend fun remind(id: String): AppResult<RequestResult>
    /** [paidBy] required by the server; receipts optional. */
    suspend fun resolve(id: String, paidBy: String, actualCost: Double?, remark: String, receipts: List<UploadFile>, onProgress: (Int) -> Unit = {}): AppResult<RequestResult>
    suspend fun cancel(id: String, remark: String): AppResult<RequestResult>
    suspend fun reopen(id: String, remark: String, files: List<UploadFile>, onProgress: (Int) -> Unit = {}): AppResult<RequestResult>
    suspend fun addPhotos(id: String, files: List<UploadFile>, onProgress: (Int) -> Unit = {}): AppResult<RequestResult>
    suspend fun update(id: String, update: RequestUpdate, files: List<UploadFile>, onProgress: (Int) -> Unit = {}): AppResult<RequestResult>
    suspend fun addInvoice(id: String, files: List<UploadFile>, onProgress: (Int) -> Unit = {}): AppResult<RequestResult>
    /** An invoice PDF (signed-in download) → a content:// uri to open; null on failure. */
    suspend fun invoicePdf(url: String): AppResult<String>
}
