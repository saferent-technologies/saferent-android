package com.saferenttechnologies.android.presentation.auth

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AuthError
import com.saferenttechnologies.android.domain.model.IndianPhone
import com.saferenttechnologies.android.domain.model.PhoneAuthEvent
import com.saferenttechnologies.android.domain.model.VerifyOtpResult
import com.saferenttechnologies.android.domain.usecase.GetAuthConfigUseCase
import com.saferenttechnologies.android.domain.usecase.GetCurrentUserUseCase
import com.saferenttechnologies.android.domain.usecase.GetLegalConfigUseCase
import com.saferenttechnologies.android.domain.usecase.HasAcceptedCurrentTermsUseCase
import com.saferenttechnologies.android.domain.usecase.SaveLoginChoicesUseCase
import com.saferenttechnologies.android.domain.usecase.SendOtpUseCase
import com.saferenttechnologies.android.domain.usecase.SignOutUseCase
import com.saferenttechnologies.android.domain.usecase.VerifyOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mobile number → OTP → signed in. Uses the auth config:
 * otpResendSeconds (resend timer) and otpMaxAttempts (wrong codes allowed per OTP).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val getAuthConfig: GetAuthConfigUseCase,
    private val getLegalConfig: GetLegalConfigUseCase,
    private val saveLoginChoices: SaveLoginChoicesUseCase,
    private val sendOtp: SendOtpUseCase,
    private val verifyOtp: VerifyOtpUseCase,
    private val hasAcceptedCurrentTerms: HasAcceptedCurrentTermsUseCase,
    private val signOut: SignOutUseCase,
    private val sessionEvents: SessionEvents,
) : MviViewModel<AuthState, AuthIntent, AuthEffect>(AuthState()) {

    private var resendSeconds = DEFAULT_RESEND_SECONDS
    private var maxAttempts = DEFAULT_MAX_ATTEMPTS
    private var sendJob: Job? = null
    private var timerJob: Job? = null
    private var sessionJob: Job? = null

    override suspend fun handleIntent(intent: AuthIntent) {
        when (intent) {
            AuthIntent.Load -> load()
            is AuthIntent.PhoneChanged -> setState { copy(phoneDigits = IndianPhone.sanitize(intent.text), error = null) }
            is AuthIntent.ToggleWhatsapp -> setState { copy(whatsappOptIn = intent.enabled) }
            is AuthIntent.ToggleTerms -> setState {
                copy(
                    termsAccepted = intent.accepted,
                    consentTimeMillis = if (intent.accepted) System.currentTimeMillis() else null,
                    error = null,
                )
            }
            AuthIntent.OpenTerms -> currentState.termsUrl.takeIf { it.isNotBlank() }?.let { sendEffect(AuthEffect.OpenUrl(it)) }
            AuthIntent.OpenPrivacy -> currentState.privacyUrl.takeIf { it.isNotBlank() }?.let { sendEffect(AuthEffect.OpenUrl(it)) }
            AuthIntent.ContinueClicked -> continueWithOtp()
            is AuthIntent.OtpDigit -> if (intent.digit.isDigit() && currentState.otp.length < VerifyOtpUseCase.OTP_LENGTH) {
                setState { copy(otp = otp + intent.digit, error = null) }
            }
            is AuthIntent.OtpChanged -> setState {
                copy(otp = intent.text.filter(Char::isDigit).take(VerifyOtpUseCase.OTP_LENGTH), error = null)
            }
            AuthIntent.OtpBackspace -> setState { copy(otp = otp.dropLast(1), error = null) }
            AuthIntent.VerifyClicked -> verify()
            AuthIntent.ResendClicked -> if (currentState.canResend) startSending(resend = true)
            AuthIntent.EditNumber -> backToPhone()
        }
    }

    private suspend fun load() {
        observeSession()
        if (currentState.isLoaded) return
        val auth = getAuthConfig()
        val legal = getLegalConfig()
        val termsDone = hasAcceptedCurrentTerms()
        resendSeconds = auth.otpResendSeconds.coerceIn(10, 600)
        maxAttempts = auth.otpMaxAttempts.coerceIn(1, 20)
        setState {
            copy(
                isLoaded = true,
                isSignedIn = getCurrentUser() != null,
                attemptsLeft = maxAttempts,
                termsUrl = legal.termsUrl,
                privacyUrl = legal.privacyPolicyUrl,
                termsAccepted = false, // always unticked by default
                termsAlreadyAccepted = termsDone,
            )
        }
    }

    private suspend fun continueWithOtp() {
        val state = currentState
        if (state.isSendingOtp) return
        when {
            !state.isPhoneValid -> return setState { copy(error = "Please enter a valid 10-digit mobile number.") }
            !state.termsAccepted -> return setState { copy(error = "Please accept the Terms of Service and Privacy Policy.") }
        }
        // Kept locally (no API here) → sent with POST userProfile.
        saveLoginChoices(state.whatsappOptIn, state.consentTimeMillis ?: System.currentTimeMillis())
        startSending(resend = false)
    }

    private fun startSending(resend: Boolean) {
        setState { copy(isSendingOtp = true, error = null) }
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            sendOtp(currentState.phoneDigits, resend).collect { onPhoneAuthEvent(it) }
        }
    }

    private fun onPhoneAuthEvent(event: PhoneAuthEvent) {
        when (event) {
            is PhoneAuthEvent.CodeSent -> {
                setState {
                    copy(
                        step = AuthStep.OTP,
                        isSendingOtp = false,
                        verificationId = event.verificationId,
                        otp = "",
                        isDetectingSms = true,
                        attemptsLeft = maxAttempts,
                        error = null,
                    )
                }
                startResendTimer()
            }
            // Firebase signs in by itself right after this → SignedIn follows.
            is PhoneAuthEvent.CodeAutoRetrieved -> setState {
                copy(otp = event.code.take(VerifyOtpUseCase.OTP_LENGTH), isDetectingSms = false, isVerifying = true)
            }
            PhoneAuthEvent.AutoRetrievalTimedOut -> setState { copy(isDetectingSms = false) }
            is PhoneAuthEvent.SignedIn -> onSignedIn()
            is PhoneAuthEvent.Failed -> setState {
                copy(isSendingOtp = false, isVerifying = false, isDetectingSms = false, error = event.error.message)
            }
        }
    }

    private suspend fun verify() {
        val state = currentState
        val verificationId = state.verificationId ?: return
        if (state.isVerifying) return
        when {
            state.attemptsLeft <= 0 -> return setState { copy(error = "Too many wrong attempts. Please resend the code.") }
            state.otp.length != VerifyOtpUseCase.OTP_LENGTH -> return setState { copy(error = "Enter the 6-digit code.") }
        }
        setState { copy(isVerifying = true, error = null) }
        when (val result = verifyOtp(verificationId, state.otp)) {
            is VerifyOtpResult.Success -> onSignedIn()
            is VerifyOtpResult.Failed -> {
                val left = if (result.error is AuthError.InvalidCode) state.attemptsLeft - 1 else state.attemptsLeft
                setState {
                    copy(
                        isVerifying = false,
                        otp = "",
                        attemptsLeft = left,
                        error = if (left <= 0) "Too many wrong attempts. Please resend the code." else result.error.message,
                    )
                }
            }
        }
    }

    /** Firebase signed in → POST auth/phone/complete runs next (PostLoginViewModel). */
    private fun onSignedIn() {
        timerJob?.cancel()
        setState { copy(isSignedIn = true, isVerifying = false, isDetectingSms = false, error = null) }
        sendEffect(AuthEffect.SignedIn)
    }

    /** 401 from the API → sign out; any sign-out → back to a fresh login screen. */
    private fun observeSession() {
        if (sessionJob != null) return
        sessionJob = viewModelScope.launch {
            sessionEvents.events.collect { event ->
                when (event) {
                    SessionEvent.Expired -> signOut(remote = false) // token already invalid; emits SignedOut
                    // Handled app-wide (SafeRentApp → blocked screen + sign-out → SignedOut below).
                    is SessionEvent.AccountBlocked -> Unit
                    SessionEvent.SignedOut -> {
                        sendJob?.cancel()
                        timerJob?.cancel()
                        val termsDone = hasAcceptedCurrentTerms()
                        setState {
                            AuthState(
                                isLoaded = true,
                                attemptsLeft = maxAttempts,
                                termsUrl = termsUrl,
                                privacyUrl = privacyUrl,
                                termsAccepted = false, // unticked again after sign-out
                                termsAlreadyAccepted = termsDone,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun backToPhone() {
        sendJob?.cancel()
        timerJob?.cancel()
        setState {
            copy(
                step = AuthStep.PHONE,
                verificationId = null,
                otp = "",
                isSendingOtp = false,
                isVerifying = false,
                isDetectingSms = false,
                resendSecondsLeft = 0,
                error = null,
            )
        }
    }

    private fun startResendTimer() {
        timerJob?.cancel()
        setState { copy(resendSecondsLeft = resendSeconds) }
        timerJob = viewModelScope.launch {
            while (currentState.resendSecondsLeft > 0) {
                delay(1_000)
                setState { copy(resendSecondsLeft = (resendSecondsLeft - 1).coerceAtLeast(0)) }
            }
        }
    }

    private companion object {
        const val DEFAULT_RESEND_SECONDS = 60
        const val DEFAULT_MAX_ATTEMPTS = 5
    }
}
