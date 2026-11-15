package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.LeaseExitState
import com.saferenttechnologies.android.domain.model.LeaseReviewInfo
import com.saferenttechnologies.android.domain.model.LeaseSummary
import com.saferenttechnologies.android.domain.model.ReviewDraft
import com.saferenttechnologies.android.domain.model.SimpleDate

/** Server message + whether the lease closed right now (accept on/after the move-out date). */
data class LeaseActionResult(val message: String, val closed: Boolean = false)

interface LeaseRepository {
    suspend fun lease(leaseId: String): AppResult<LeaseSummary>
    suspend fun exitState(leaseId: String): AppResult<LeaseExitState>
    suspend fun requestExit(leaseId: String, reason: String, moveOut: SimpleDate, review: ReviewDraft?): AppResult<LeaseActionResult>
    suspend fun acceptExit(leaseId: String, note: String, review: ReviewDraft?): AppResult<LeaseActionResult>
    suspend fun declineExit(leaseId: String, note: String): AppResult<LeaseActionResult>
    suspend fun cancelExit(leaseId: String): AppResult<LeaseActionResult>
    suspend fun reviewInfo(leaseId: String): AppResult<LeaseReviewInfo>
    suspend fun postReview(leaseId: String, review: ReviewDraft): AppResult<LeaseActionResult>
    suspend fun pendingReviews(): AppResult<List<com.saferenttechnologies.android.domain.model.PendingReview>>
    suspend fun depositRefund(leaseId: String, answer: com.saferenttechnologies.android.domain.model.DepositRefund, amount: String): AppResult<LeaseActionResult>
}
