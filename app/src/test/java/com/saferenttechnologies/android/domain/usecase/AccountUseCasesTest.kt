package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.AuthSettings
import com.saferenttechnologies.android.domain.model.AuthUser
import com.saferenttechnologies.android.domain.model.ConsentItem
import com.saferenttechnologies.android.domain.model.ConsentStatus
import com.saferenttechnologies.android.domain.model.ConsentType
import com.saferenttechnologies.android.domain.model.Gender
import com.saferenttechnologies.android.domain.model.LegalSettings
import com.saferenttechnologies.android.domain.model.PhoneCompletion
import com.saferenttechnologies.android.domain.model.PostLoginDestination
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.UserProfileField
import com.saferenttechnologies.android.domain.model.UserProfileForm
import com.saferenttechnologies.android.domain.model.UserProfileResult
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.testutil.FakeAccountRepository
import com.saferenttechnologies.android.testutil.FakeAuthRepository
import com.saferenttechnologies.android.testutil.FakeConfigRepository
import com.saferenttechnologies.android.testutil.FakeDeviceTokenRepository
import com.saferenttechnologies.android.testutil.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountUseCasesTest {

    private val calls = mutableListOf<String>()
    private val account = FakeAccountRepository(calls)
    private val deviceTokens = FakeDeviceTokenRepository(calls)
    private val config = FakeConfigRepository()
    private val session = FakeSessionRepository()
    private val auth = FakeAuthRepository()
    private val resolve = ResolvePostLoginUseCase(account, config, deviceTokens)
    private val save = SaveUserProfileUseCase(account, session, config)
    private val today = SimpleDate(2026, 11, 14)

    private fun authConfig(show: Boolean, skippable: Boolean = true) {
        config.config.value = config.config.value.copy(
            auth = AuthSettings(showDemographics = show, demographicsSkippable = skippable),
        )
    }

    private fun legal(terms: String, privacy: String) {
        config.config.value = config.config.value.copy(legal = LegalSettings(termsVersion = terms, privacyVersion = privacy))
    }

    private suspend fun destination() = (resolve() as AppResult.Success).data

    /** Terms ticked on the login screen at [at], WhatsApp = [whatsapp]. */
    private suspend fun ticked(at: Long = 1_700_000_000_000, whatsapp: Boolean = false) =
        session.saveLoginChoices(whatsapp, at, "x", "x")

    // ---- phone/complete + FCM token ----

    @Test
    fun `phone complete sends the fcm token and remembers it when saved`() = runTest {
        account.completion = AppResult.Success(PhoneCompletion(isProfileCompleted = true, fcmTokenSaved = true))
        destination()
        assertEquals(listOf<String?>("fcm-token-1"), account.completeTokens)
        assertEquals("fcm-token-1", deviceTokens.lastSent)
    }

    @Test
    fun `token fetch fails - phone complete sent without token and login still succeeds`() = runTest {
        deviceTokens.token = null
        account.completion = AppResult.Success(PhoneCompletion(isProfileCompleted = true))
        val d = destination()
        assertEquals(listOf<String?>(null), account.completeTokens) // → POST with no body
        assertTrue(d is PostLoginDestination.Dashboard)
        assertNull(deviceTokens.lastSent)
    }

    // ---- post-OTP navigation ----

    @Test
    fun `completed profile opens dashboard of saved active role`() = runTest {
        authConfig(show = true)
        account.completion = AppResult.Success(PhoneCompletion(isProfileCompleted = true))
        account.meResult = AppResult.Success(
            AccountUser(id = "u1", role = UserRole.TENANT, roles = listOf(UserRole.TENANT, UserRole.LANDLORD)),
        )
        account.savedRole = UserRole.LANDLORD
        val d = destination() as PostLoginDestination.Dashboard
        assertEquals(UserRole.LANDLORD, d.activeRole)
    }

    @Test
    fun `new account always goes to profile, not skippable, no auth me`() = runTest {
        authConfig(show = false, skippable = true)
        account.completion = AppResult.Success(PhoneCompletion(registrationRequired = true))
        val d = destination() as PostLoginDestination.UserProfile
        assertTrue(d.registrationRequired)
        assertFalse(d.skippable)
        assertNull(d.user)
        assertEquals(0, account.meCalls)
    }

    @Test
    fun `incomplete existing profile follows showDemographics`() = runTest {
        account.completion = AppResult.Success(PhoneCompletion(isProfileCompleted = false))
        authConfig(show = true, skippable = true)
        val d = destination() as PostLoginDestination.UserProfile
        assertTrue(d.skippable)
        assertEquals("u1", d.user?.id) // prefilled from auth/me

        authConfig(show = false)
        assertTrue(destination() is PostLoginDestination.Dashboard)
    }

    @Test
    fun `phone complete failure is returned`() = runTest {
        account.completion = AppResult.Failure(AppError.LoginMethodDisabled())
        val r = resolve() as AppResult.Failure
        assertEquals("Phone sign-in is turned off right now", r.error.message)
    }

    // ---- POST userProfile ----

    private val form = UserProfileForm(
        fullName = " Sita ", role = UserRole.LANDLORD, gender = Gender.FEMALE, dateOfBirth = "1995-06-01",
        city = "Vizag", state = "AP", email = "  ", occupation = "Teacher", referralCode = " ABC ",
    )

    @Test
    fun `request has consents with config versions, whatsapp flag and clientAcceptedAt`() = runTest {
        legal("2026-11-01", "2026-11-02")
        ticked(at = 1_731_576_600_000, whatsapp = true)
        save(form, registrationRequired = true, today = today)

        val req = account.requests.single()
        assertEquals(
            listOf(
                ConsentItem(ConsentType.TERMS, "2026-11-01", granted = true),
                ConsentItem(ConsentType.PRIVACY, "2026-11-02", granted = true),
                ConsentItem(ConsentType.WHATSAPP, "", granted = true),
            ),
            req.consents,
        )
        assertEquals(1_731_576_600_000, req.clientAcceptedAt)
        assertNull(session.getLoginConsent()) // sent → cleared
    }

    @Test
    fun `new user without login consent is never sent`() = runTest {
        val r = save(form, registrationRequired = true, today = today) as SaveUserProfileResult.Invalid
        assertTrue(UserProfileField.CONSENTS in r.fieldErrors)
        assertTrue(account.requests.isEmpty())
    }

    @Test
    fun `existing user without consent is saved without consents`() = runTest {
        save(form, registrationRequired = false, today = today)
        val req = account.requests.single()
        assertNull(req.consents)
        assertNull(req.clientAcceptedAt)
    }

    @Test
    fun `invalid form never calls the api`() = runTest {
        ticked()
        val r = save(UserProfileForm(), registrationRequired = true, today = today)
        assertTrue(r is SaveUserProfileResult.Invalid)
        assertTrue(account.requests.isEmpty())
    }

    @Test
    fun `request trims, blank email null, occupation tenant only, referral only when registering`() = runTest {
        save(form, registrationRequired = false, today = today)
        val req = account.requests.single()
        assertTrue(req.isProfileCompleted)
        assertEquals("Sita", req.fullName)
        assertNull(req.email)
        assertNull(req.occupation) // landlord
        assertNull(req.referralCode) // existing account

        val tenant = SaveUserProfileUseCase.buildRequest(
            form.copy(role = UserRole.TENANT), registrationRequired = true, consent = null, legal = LegalSettings(),
        )
        assertEquals("Teacher", tenant.occupation)
        assertEquals("ABC", tenant.referralCode)
    }

    @Test
    fun `response role becomes active role and opens that dashboard`() = runTest {
        account.saveResult = AppResult.Success(
            UserProfileResult(
                user = AccountUser(id = "u1", role = UserRole.LANDLORD),
                isProfileCompleted = true, missingFields = emptyList(),
                role = UserRole.LANDLORD, roles = listOf(UserRole.LANDLORD),
                termsAccepted = true, consents = ConsentStatus(), created = true,
            ),
        )
        val r = save(form.copy(role = UserRole.TENANT), false, today) as SaveUserProfileResult.Saved
        assertEquals(UserRole.LANDLORD, r.activeRole)
        assertEquals(UserRole.LANDLORD, account.savedRole)
        assertTrue(r.created)
    }

    @Test
    fun `server 400 and 409 become field errors, consents goes back to login`() = runTest {
        account.saveResult = AppResult.Failure(AppError.Validation("Please fill all fields", listOf("city")))
        val bad = save(form, false, today) as SaveUserProfileResult.Invalid
        assertEquals(setOf(UserProfileField.CITY), bad.fieldErrors.keys)
        assertEquals("Please fill all fields", bad.message)

        account.saveResult = AppResult.Failure(AppError.Validation("Consents required", listOf("consents")))
        val consents = save(form, false, today) as SaveUserProfileResult.Invalid
        assertTrue(UserProfileField.CONSENTS in consents.fieldErrors)

        account.saveResult = AppResult.Failure(AppError.EmailInUse())
        val email = save(form.copy(email = "a@b.co"), false, today) as SaveUserProfileResult.Invalid
        assertTrue(UserProfileField.EMAIL in email.fieldErrors)
    }

    @Test
    fun `skip sends only incomplete + current role then opens dashboard`() = runTest {
        val user = AccountUser(id = "u1", role = UserRole.TENANT, roles = listOf(UserRole.TENANT))
        val r = SkipUserProfileUseCase(account)(user) as AppResult.Success
        val req = account.requests.single()
        assertFalse(req.isProfileCompleted)
        assertEquals(UserRole.TENANT, req.role)
        assertNull(req.consents)
        assertEquals(UserRole.TENANT, r.data.activeRole)
    }

    @Test
    fun `skip uses the role picked on the screen`() = runTest {
        val user = AccountUser(id = "u1", role = UserRole.TENANT, roles = listOf(UserRole.TENANT))
        account.savedRole = UserRole.TENANT
        val r = SkipUserProfileUseCase(account)(user, UserRole.LANDLORD) as AppResult.Success
        assertEquals(UserRole.LANDLORD, account.requests.single().role)
        assertEquals(UserRole.LANDLORD, r.data.activeRole)
        assertEquals(UserRole.LANDLORD, account.savedRole)
    }

    // ---- FCM token lifecycle ----

    private val sync = SyncFcmTokenUseCase(auth, account, deviceTokens)

    @Test
    fun `onNewToken - sent only when signed in and changed`() = runTest {
        assertEquals(FcmTokenSync.SIGNED_OUT, sync("new-token"))
        assertTrue(account.fcmTokens.isEmpty())

        auth.user = AuthUser("uid", "+919849012345")
        assertEquals(FcmTokenSync.SENT, sync("new-token"))
        assertEquals("new-token", deviceTokens.lastSent)

        assertEquals(FcmTokenSync.UNCHANGED, sync("new-token"))
        assertEquals(listOf("new-token"), account.fcmTokens) // only one PUT
    }

    @Test
    fun `fcm token network failure is retried, 4xx is not`() = runTest {
        auth.user = AuthUser("uid", null)
        account.fcmResult = AppResult.Failure(AppError.Network())
        assertEquals(FcmTokenSync.FAILED, sync("t1"))
        assertNull(deviceTokens.lastSent)

        account.fcmResult = AppResult.Failure(AppError.Validation("bad token"))
        assertEquals(FcmTokenSync.REJECTED, sync("t1"))
    }

    @Test
    fun `logout sends the fcm token first, then signs out`() = runTest {
        auth.user = AuthUser("uid", null)
        deviceTokens.lastSent = "fcm-token-1"
        ticked()
        val events = SessionEvents()
        SignOutUseCase(auth, account, deviceTokens, session, events)()

        assertEquals(listOf("logout:fcm-token-1", "clearToken"), calls)
        assertNull(auth.user)
        assertNull(deviceTokens.lastSent)
        assertNull(session.getLoginConsent())
    }

    @Test
    fun `session expiry signs out without calling logout`() = runTest {
        auth.user = AuthUser("uid", null)
        SignOutUseCase(auth, account, deviceTokens, session, SessionEvents())(remote = false)
        assertFalse(calls.any { it.startsWith("logout") })
        assertNull(auth.user)
    }
}
