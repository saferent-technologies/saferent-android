package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.AuthError
import com.saferenttechnologies.android.domain.model.AuthUser
import com.saferenttechnologies.android.domain.model.IndianPhone
import com.saferenttechnologies.android.domain.model.PhoneAuthEvent
import com.saferenttechnologies.android.domain.model.VerifyOtpResult
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.repository.AccountRepository
import com.saferenttechnologies.android.domain.repository.AuthRepository
import com.saferenttechnologies.android.domain.repository.DeviceTokenRepository
import com.saferenttechnologies.android.domain.repository.ConfigRepository
import com.saferenttechnologies.android.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(private val auth: AuthRepository) {
    operator fun invoke(): AuthUser? = auth.currentUser()
}

/** Validates the 10-digit number, then sends the OTP (+91). */
class SendOtpUseCase @Inject constructor(private val auth: AuthRepository) {
    operator fun invoke(digits: String, resend: Boolean = false): Flow<PhoneAuthEvent> {
        val clean = IndianPhone.sanitize(digits)
        if (!IndianPhone.isValid(clean)) return flowOf(PhoneAuthEvent.Failed(AuthError.InvalidPhone()))
        return auth.sendOtp(IndianPhone.toE164(clean), resend)
    }
}

class VerifyOtpUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(verificationId: String, code: String): VerifyOtpResult {
        if (code.length != OTP_LENGTH || !code.all(Char::isDigit)) {
            return VerifyOtpResult.Failed(AuthError.InvalidCode("Enter the 6-digit code."))
        }
        return auth.verifyOtp(verificationId, code)
    }

    companion object {
        const val OTP_LENGTH = 6
    }
}

class SaveLoginChoicesUseCase @Inject constructor(
    private val session: SessionRepository,
    private val config: ConfigRepository,
) {
    /** [consentTimeMillis] = when Terms was ticked. Kept until sign-out, sent with POST userProfile. */
    suspend operator fun invoke(whatsappOptIn: Boolean, consentTimeMillis: Long) {
        val legal = config.getLegal()
        session.saveLoginChoices(
            whatsappOptIn = whatsappOptIn,
            termsAcceptedAt = consentTimeMillis,
            termsVersion = legal.termsVersion,
            privacyVersion = legal.privacyVersion,
        )
    }
}

/**
 * Terms are asked only once: true when the CURRENT Terms + Privacy versions were already
 * accepted on this phone (e.g. signing in again after a sign-out). A new version → ask again.
 */
class HasAcceptedCurrentTermsUseCase @Inject constructor(
    private val session: SessionRepository,
    private val config: ConfigRepository,
) {
    suspend operator fun invoke(): Boolean {
        val legal = config.getLegal()
        return session.hasAcceptedTerms(legal.termsVersion, legal.privacyVersion)
    }
}

/**
 * Sign out. [remote] = tell the server first (POST auth/logout { fcmToken } → it deletes this
 * device's push token), then Firebase sign-out. Remote failure never blocks signing out.
 * A 401 session expiry signs out with remote = false (the token is already invalid).
 */
class SignOutUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val deviceTokens: DeviceTokenRepository,
    private val session: SessionRepository,
    private val sessionEvents: SessionEvents,
) {
    suspend operator fun invoke(remote: Boolean = true) {
        if (remote && auth.currentUser() != null) {
            account.logout(deviceTokens.currentToken())
        }
        auth.signOut()
        deviceTokens.clearLastSentToken()
        session.clearLoginConsent()
        sessionEvents.emit(SessionEvent.SignedOut)
    }
}
