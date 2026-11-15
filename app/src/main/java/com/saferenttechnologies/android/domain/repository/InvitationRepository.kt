package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.InvitationDecision
import com.saferenttechnologies.android.domain.model.TenantInvitation

interface InvitationRepository {
    suspend fun invitation(token: String): AppResult<TenantInvitation>
    suspend fun accept(token: String): AppResult<InvitationDecision>
    suspend fun reject(token: String, reason: String): AppResult<InvitationDecision>
}
