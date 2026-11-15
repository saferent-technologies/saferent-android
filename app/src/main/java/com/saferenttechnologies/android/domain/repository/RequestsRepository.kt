package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RequestOptions
import com.saferenttechnologies.android.domain.model.RequestPage
import com.saferenttechnologies.android.domain.model.RequestSummary
import com.saferenttechnologies.android.domain.model.ServiceRequest

/** Server message + the request as it is now (every action returns it). */
data class RequestResult(val message: String, val request: ServiceRequest?)

interface RequestsRepository {
    /** Cached after the first success. */
    suspend fun options(): AppResult<RequestOptions>
    suspend fun create(type: String, category: String, description: String, priority: String, leaseId: String?, photos: List<ByteArray>): AppResult<RequestResult>
    suspend fun list(status: String?, landlord: Boolean, page: Int): AppResult<RequestPage>
    suspend fun summary(): AppResult<RequestSummary>
    suspend fun detail(id: String): AppResult<ServiceRequest>
    suspend fun remind(id: String): AppResult<RequestResult>
    suspend fun resolve(id: String, remark: String): AppResult<RequestResult>
    suspend fun cancel(id: String, remark: String): AppResult<RequestResult>
    suspend fun reopen(id: String, remark: String, photos: List<ByteArray>): AppResult<RequestResult>
    suspend fun addPhotos(id: String, photos: List<ByteArray>): AppResult<RequestResult>
    suspend fun update(id: String, status: String?, remark: String, photos: List<ByteArray>): AppResult<RequestResult>
}
