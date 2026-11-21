package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.InvitationDecision
import com.saferenttechnologies.android.domain.model.TenantInvitation

interface InvitationRepository {
    suspend fun invitation(token: String): AppResult<TenantInvitation>
    /** Tenant dashboard: my invitations still open (PENDING, not expired). */
    suspend fun mine(): AppResult<List<TenantInvitation>>
    /** [answers] only when the invitation asks something (sent as "details"); null = empty body. */
    suspend fun accept(token: String, answers: com.saferenttechnologies.android.domain.model.TenantAnswers? = null, requirements: com.saferenttechnologies.android.domain.model.InviteRequirements? = null): AppResult<InvitationDecision>
    suspend fun reject(token: String, reason: String): AppResult<InvitationDecision>
}
