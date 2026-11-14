package com.saferenttechnologies.android.testutil

import com.saferenttechnologies.android.domain.model.LoginConsent
import com.saferenttechnologies.android.domain.model.AuthUser
import com.saferenttechnologies.android.domain.model.PhoneAuthEvent
import com.saferenttechnologies.android.domain.model.VerifyOtpResult
import com.saferenttechnologies.android.domain.repository.AuthRepository
import com.saferenttechnologies.android.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeAuthRepository : AuthRepository {
    var user: AuthUser? = null
    /** Tests emit Firebase-like events into this. */
    val events = MutableSharedFlow<PhoneAuthEvent>(extraBufferCapacity = 16)
    val sentTo = mutableListOf<Pair<String, Boolean>>()
    var verifyResult: VerifyOtpResult = VerifyOtpResult.Success(AuthUser("uid-1", "+919849012345"))
    val verifiedCodes = mutableListOf<String>()

    override fun currentUser(): AuthUser? = user

    override fun sendOtp(phoneE164: String, resend: Boolean): Flow<PhoneAuthEvent> {
        sentTo += phoneE164 to resend
        return events
    }

    override suspend fun verifyOtp(verificationId: String, code: String): VerifyOtpResult {
        verifiedCodes += code
        return verifyResult
    }

    override suspend fun signOut() {
        user = null
    }
}

class FakeSessionRepository : SessionRepository {
    var whatsappOptIn: Boolean? = null
    var termsAcceptedAt: Long? = null
    var acceptedVersions: Pair<String, String>? = null

    override suspend fun hasAcceptedTerms(termsVersion: String, privacyVersion: String): Boolean =
        acceptedVersions == (termsVersion to privacyVersion)

    override suspend fun saveLoginChoices(
        whatsappOptIn: Boolean,
        termsAcceptedAt: Long,
        termsVersion: String,
        privacyVersion: String,
    ) {
        this.acceptedVersions = termsVersion to privacyVersion
        this.whatsappOptIn = whatsappOptIn
        this.termsAcceptedAt = termsAcceptedAt
    }

    override suspend fun getLoginConsent(): LoginConsent? =
        termsAcceptedAt?.let { LoginConsent(termsAccepted = true, whatsappOptIn = whatsappOptIn ?: false, consentTimeMillis = it) }

    override suspend fun clearLoginConsent() {
        termsAcceptedAt = null
        whatsappOptIn = null
    }
}
