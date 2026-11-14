package com.saferenttechnologies.android.presentation.auth

import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.IndianPhone
import com.saferenttechnologies.android.domain.usecase.VerifyOtpUseCase

enum class AuthStep { PHONE, OTP }

data class AuthState(
    val isLoaded: Boolean = false,
    val isSignedIn: Boolean = false,
    val step: AuthStep = AuthStep.PHONE,
    // --- mobile entry ---
    val phoneDigits: String = "",
    /** Optional WhatsApp reminders opt-in — unticked by default (never opt users in silently). */
    val whatsappOptIn: Boolean = false,
    /** Terms of Service & Privacy Policy ticked — required to continue. Always starts unticked. */
    val termsAccepted: Boolean = false,
    /** When Terms was ticked (System.currentTimeMillis()); sent later as clientAcceptedAt. */
    val consentTimeMillis: Long? = null,
    /** Current Terms were already accepted on this phone (info only; box still starts unticked). */
    val termsAlreadyAccepted: Boolean = false,
    val isSendingOtp: Boolean = false,
    // --- OTP ---
    val verificationId: String? = null,
    val otp: String = "",
    val isVerifying: Boolean = false,
    /** Waiting for Android to read the SMS (shows "Detecting OTP from SMS…"). */
    val isDetectingSms: Boolean = false,
    val resendSecondsLeft: Int = 0,
    val attemptsLeft: Int = 5,
    val error: String? = null,
    // --- from config ---
    val termsUrl: String = "",
    val privacyUrl: String = "",
) : UiState {
    val isPhoneValid: Boolean get() = IndianPhone.isValid(phoneDigits)
    val canContinue: Boolean get() = isPhoneValid && termsAccepted && !isSendingOtp
    val canVerify: Boolean
        get() = otp.length == VerifyOtpUseCase.OTP_LENGTH && !isVerifying && verificationId != null && attemptsLeft > 0
    val canResend: Boolean get() = resendSecondsLeft == 0 && !isSendingOtp && !isVerifying
    val displayPhone: String get() = "${IndianPhone.COUNTRY_CODE} ${IndianPhone.format(phoneDigits)}"
}

sealed interface AuthIntent : UiIntent {
    data object Load : AuthIntent
    data class PhoneChanged(val text: String) : AuthIntent
    data class ToggleWhatsapp(val enabled: Boolean) : AuthIntent
    data class ToggleTerms(val accepted: Boolean) : AuthIntent
    data object OpenTerms : AuthIntent
    data object OpenPrivacy : AuthIntent
    data object ContinueClicked : AuthIntent
    /** System keyboard input (digits only, max 6). */
    data class OtpChanged(val text: String) : AuthIntent
    data class OtpDigit(val digit: Char) : AuthIntent
    data object OtpBackspace : AuthIntent
    data object VerifyClicked : AuthIntent
    data object ResendClicked : AuthIntent
    /** "Edit" number or back from the OTP step. */
    data object EditNumber : AuthIntent
}

sealed interface AuthEffect : UiEffect {
    data object SignedIn : AuthEffect
    /** Open a policy page (Custom Tab). */
    data class OpenUrl(val url: String) : AuthEffect
}
