package com.saferenttechnologies.android.presentation.auth

import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.AuthError
import com.saferenttechnologies.android.domain.model.AuthSettings
import com.saferenttechnologies.android.domain.model.AuthUser
import com.saferenttechnologies.android.domain.model.PhoneAuthEvent
import com.saferenttechnologies.android.domain.model.VerifyOtpResult
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.usecase.GetAuthConfigUseCase
import com.saferenttechnologies.android.domain.usecase.SignOutUseCase
import com.saferenttechnologies.android.testutil.FakeAccountRepository
import com.saferenttechnologies.android.testutil.FakeDeviceTokenRepository
import com.saferenttechnologies.android.domain.usecase.GetCurrentUserUseCase
import com.saferenttechnologies.android.domain.usecase.GetLegalConfigUseCase
import com.saferenttechnologies.android.domain.usecase.HasAcceptedCurrentTermsUseCase
import com.saferenttechnologies.android.domain.usecase.SaveLoginChoicesUseCase
import com.saferenttechnologies.android.domain.usecase.SendOtpUseCase
import com.saferenttechnologies.android.domain.usecase.VerifyOtpUseCase
import com.saferenttechnologies.android.testutil.FakeAuthRepository
import com.saferenttechnologies.android.testutil.FakeConfigRepository
import com.saferenttechnologies.android.testutil.FakeSessionRepository
import com.saferenttechnologies.android.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val session = FakeSessionRepository()
    private val calls = mutableListOf<String>()
    private val account = FakeAccountRepository(calls)
    private val deviceTokens = FakeDeviceTokenRepository(calls)
    private val sessionEvents = SessionEvents()
    private val config = FakeConfigRepository().apply {
        config.value = AppConfig(auth = AuthSettings(otpResendSeconds = 30, otpMaxAttempts = 2))
    }

    private fun viewModel() = AuthViewModel(
        GetCurrentUserUseCase(auth),
        GetAuthConfigUseCase(config),
        GetLegalConfigUseCase(config),
        SaveLoginChoicesUseCase(session, config),
        SendOtpUseCase(auth),
        VerifyOtpUseCase(auth),
        HasAcceptedCurrentTermsUseCase(session, config),
        SignOutUseCase(auth, account, deviceTokens, session, sessionEvents),
        sessionEvents,
    ).also { it.onIntent(AuthIntent.Load) }

    private fun AuthViewModel.typeOtp(code: String) = onIntent(AuthIntent.OtpChanged(code))

    private fun AuthViewModel.toOtpStep() {
        onIntent(AuthIntent.PhoneChanged("98490 12345"))
        onIntent(AuthIntent.ToggleTerms(true))
        onIntent(AuthIntent.ContinueClicked)
        auth.events.tryEmit(PhoneAuthEvent.CodeSent("verification-1"))
    }

    @Test
    fun `already signed in user skips login`() = runTest {
        auth.user = AuthUser("uid", "+919849012345")
        assertTrue(viewModel().state.value.isSignedIn)
    }

    @Test
    fun `continue needs a valid number AND accepted terms`() = runTest {
        val vm = viewModel()
        vm.onIntent(AuthIntent.ToggleTerms(true))
        vm.onIntent(AuthIntent.PhoneChanged("12345"))
        assertFalse(vm.state.value.canContinue)

        vm.onIntent(AuthIntent.PhoneChanged("+91 98490 12345"))
        assertEquals("9849012345", vm.state.value.phoneDigits)
        assertTrue(vm.state.value.canContinue)

        vm.onIntent(AuthIntent.ToggleTerms(false))
        assertFalse(vm.state.value.canContinue)
        vm.onIntent(AuthIntent.ContinueClicked)
        assertTrue(auth.sentTo.isEmpty()) // no OTP without consent
        assertEquals("Please accept the Terms of Service and Privacy Policy.", vm.state.value.error)

        vm.onIntent(AuthIntent.ToggleTerms(true))
        assertEquals(null, vm.state.value.error)
        vm.onIntent(AuthIntent.PhoneChanged("123"))
        vm.onIntent(AuthIntent.ContinueClicked)
        assertEquals("Please enter a valid 10-digit mobile number.", vm.state.value.error)
        assertTrue(auth.sentTo.isEmpty())
    }

    @Test
    fun `continue saves role + whatsapp, sends OTP and moves to the OTP step with a resend timer`() = runTest {
        val vm = viewModel()
        vm.onIntent(AuthIntent.ToggleWhatsapp(false))
        vm.toOtpStep()
        runCurrent()

        assertEquals(listOf("+919849012345" to false), auth.sentTo)
        assertEquals(false, session.whatsappOptIn)
        assertTrue(session.termsAcceptedAt != null)
        with(vm.state.value) {
            assertEquals(AuthStep.OTP, step)
            assertEquals("verification-1", verificationId)
            assertTrue(isDetectingSms)
            assertEquals(30, resendSecondsLeft)
            assertFalse(canResend)
        }

        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(0, vm.state.value.resendSecondsLeft)
        assertTrue(vm.state.value.canResend)
    }

    @Test
    fun `typing 6 digits enables verify, success signs in`() = runTest {
        val vm = viewModel()
        vm.toOtpStep()
        vm.typeOtp("58290")
        assertFalse(vm.state.value.canVerify)
        vm.typeOtp("1")
        assertTrue(vm.state.value.canVerify)

        vm.onIntent(AuthIntent.VerifyClicked)
        runCurrent()
        assertEquals(listOf("582901"), auth.verifiedCodes)
        assertTrue(vm.state.value.isSignedIn)
    }

    @Test
    fun `wrong code uses an attempt, last wrong attempt asks to resend`() = runTest {
        auth.verifyResult = VerifyOtpResult.Failed(AuthError.InvalidCode())
        val vm = viewModel()
        vm.toOtpStep()

        vm.typeOtp("111111")
        vm.onIntent(AuthIntent.VerifyClicked)
        runCurrent()
        assertEquals(1, vm.state.value.attemptsLeft)
        assertEquals("", vm.state.value.otp)

        vm.typeOtp("222222")
        vm.onIntent(AuthIntent.VerifyClicked)
        runCurrent()
        assertEquals(0, vm.state.value.attemptsLeft)
        assertFalse(vm.state.value.canVerify)
        assertTrue(vm.state.value.error!!.contains("resend", ignoreCase = true))
    }

    @Test
    fun `SMS auto-retrieved fills the code, then signed in`() = runTest {
        val vm = viewModel()
        vm.toOtpStep()
        auth.events.tryEmit(PhoneAuthEvent.CodeAutoRetrieved("482913"))
        assertEquals("482913", vm.state.value.otp)
        assertFalse(vm.state.value.isDetectingSms)

        auth.events.tryEmit(PhoneAuthEvent.SignedIn(AuthUser("uid", "+919849012345")))
        assertTrue(vm.state.value.isSignedIn)
    }

    @Test
    fun `edit number returns to the phone step and clears the OTP`() = runTest {
        val vm = viewModel()
        vm.toOtpStep()
        vm.typeOtp("123")
        vm.onIntent(AuthIntent.EditNumber)

        with(vm.state.value) {
            assertEquals(AuthStep.PHONE, step)
            assertEquals("", otp)
            assertNull(verificationId)
            assertEquals("9849012345", phoneDigits) // number kept for editing
        }
    }

    @Test
    fun `send failure shows the error and stays on the phone step`() = runTest {
        val vm = viewModel()
        vm.onIntent(AuthIntent.PhoneChanged("9849012345"))
        vm.onIntent(AuthIntent.ToggleTerms(true))
        vm.onIntent(AuthIntent.ContinueClicked)
        auth.events.tryEmit(PhoneAuthEvent.Failed(AuthError.TooManyRequests()))

        assertEquals(AuthStep.PHONE, vm.state.value.step)
        assertFalse(vm.state.value.isSendingOtp)
        assertEquals(AuthError.TooManyRequests().message, vm.state.value.error)
    }

    @Test
    fun `ticking terms keeps the choice and the time for the profile screen`() = runTest {
        val vm = viewModel()
        vm.onIntent(AuthIntent.ToggleWhatsapp(true))
        vm.toOtpStep()
        val ticked = vm.state.value.consentTimeMillis
        assertTrue(ticked != null)

        val stored = session.getLoginConsent()!!
        assertEquals(true, stored.whatsappOptIn)
        assertEquals(ticked, stored.consentTimeMillis)

        vm.typeOtp("123456")
        vm.onIntent(AuthIntent.VerifyClicked)
        assertTrue(vm.state.value.isSignedIn)
        assertTrue(calls.isEmpty()) // no consent API at login any more — sent with POST userProfile
    }

    @Test
    fun `unticking terms clears the consent time`() = runTest {
        val vm = viewModel()
        vm.onIntent(AuthIntent.ToggleTerms(true))
        vm.onIntent(AuthIntent.ToggleTerms(false))
        assertNull(vm.state.value.consentTimeMillis)
    }

    @Test
    fun `401 session expiry signs out and returns to a fresh login`() = runTest {
        auth.user = AuthUser("uid", "+919849012345")
        val vm = viewModel()
        assertTrue(vm.state.value.isSignedIn)

        sessionEvents.emit(SessionEvent.Expired)

        assertFalse(vm.state.value.isSignedIn)
        assertEquals(AuthStep.PHONE, vm.state.value.step)
        assertEquals(null, auth.user)
    }

    @Test
    fun `whatsapp opt-in is unticked by default`() = runTest {
        assertFalse(viewModel().state.value.whatsappOptIn)
    }

    @Test
    fun `terms box always starts unticked even when accepted before`() = runTest {
        val first = viewModel()
        first.toOtpStep() // ticks terms + continues → saves the accepted versions
        assertEquals("2026-11-01" to "2026-11-01", session.acceptedVersions)

        val next = viewModel() // e.g. next sign-in on this phone
        assertTrue(next.state.value.termsAlreadyAccepted)
        assertFalse(next.state.value.termsAccepted)
        next.onIntent(AuthIntent.PhoneChanged("9849012345"))
        assertFalse(next.state.value.canContinue) // must tick again
    }

    @Test
    fun `new terms version asks again`() = runTest {
        session.acceptedVersions = "2025-01-01" to "2026-11-01"
        val vm = viewModel()
        assertFalse(vm.state.value.termsAlreadyAccepted)
        assertFalse(vm.state.value.termsAccepted)
    }
}
