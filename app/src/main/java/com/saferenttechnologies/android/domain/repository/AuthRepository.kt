package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AuthUser
import com.saferenttechnologies.android.domain.model.LoginConsent
import com.saferenttechnologies.android.domain.model.PhoneAuthEvent
import com.saferenttechnologies.android.domain.model.VerifyOtpResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Signed-in user, or null. */
    fun currentUser(): AuthUser?

    /**
     * Sends an OTP to [phoneE164] (e.g. "+919849012345") and streams what happens next
     * (code sent, auto-detected, signed in, failed). [resend] re-sends to the same number.
     */
    fun sendOtp(phoneE164: String, resend: Boolean = false): Flow<PhoneAuthEvent>

    suspend fun verifyOtp(verificationId: String, code: String): VerifyOtpResult

    suspend fun signOut()
}

/** Small local session facts (DataStore "saferent_session"). */
interface SessionRepository {
    /**
     * Saves the login consent: WhatsApp choice + which Terms / Privacy versions were accepted
     * and when ([termsAcceptedAt], epoch ms).
     */
    suspend fun saveLoginChoices(
        whatsappOptIn: Boolean,
        termsAcceptedAt: Long,
        termsVersion: String,
        privacyVersion: String,
    )

    /** Consent ticked on the login screen (kept until sign-out, survives app restarts). */
    suspend fun getLoginConsent(): LoginConsent?

    suspend fun clearLoginConsent()

    /** True if these exact Terms + Privacy versions were already accepted on this phone. */
    suspend fun hasAcceptedTerms(termsVersion: String, privacyVersion: String): Boolean
}
