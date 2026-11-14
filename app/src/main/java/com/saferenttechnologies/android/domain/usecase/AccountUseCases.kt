package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.ActiveRole
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.AuthSettings
import com.saferenttechnologies.android.domain.model.ConsentItem
import com.saferenttechnologies.android.domain.model.ConsentType
import com.saferenttechnologies.android.domain.model.LegalSettings
import com.saferenttechnologies.android.domain.model.LoginConsent
import com.saferenttechnologies.android.domain.model.PhoneCompletion
import com.saferenttechnologies.android.domain.model.PostLoginDestination
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.UserProfileField
import com.saferenttechnologies.android.domain.model.UserProfileForm
import com.saferenttechnologies.android.domain.model.UserProfileRequest
import com.saferenttechnologies.android.domain.model.UserProfileValidator
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.domain.repository.AccountRepository
import com.saferenttechnologies.android.domain.repository.AuthRepository
import com.saferenttechnologies.android.domain.repository.ConfigRepository
import com.saferenttechnologies.android.domain.repository.DeviceTokenRepository
import com.saferenttechnologies.android.domain.repository.SessionRepository
import java.util.Calendar
import javax.inject.Inject

/** Pure post-OTP navigation rules. */
object PostLoginRules {
    /** True → UserProfile screen, false → dashboard. */
    fun needsProfile(completion: PhoneCompletion, auth: AuthSettings): Boolean =
        !completion.isProfileCompleted && (completion.registrationRequired || auth.showDemographics)

    /** Skip only for an existing account and when the config allows it. */
    fun canSkip(registrationRequired: Boolean, auth: AuthSettings): Boolean =
        !registrationRequired && auth.demographicsSkippable
}

/**
 * After Firebase OTP (and on app start while signed in):
 * POST auth/phone/complete (+ FCM token when available) → GET auth/me → dashboard, or UserProfile.
 */
class ResolvePostLoginUseCase @Inject constructor(
    private val account: AccountRepository,
    private val config: ConfigRepository,
    private val deviceTokens: DeviceTokenRepository,
) {
    suspend operator fun invoke(): AppResult<PostLoginDestination> {
        val token = deviceTokens.currentToken() // null on failure → no body; login continues
        val completion = when (val r = account.completePhoneSignIn(token)) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return r
        }
        if (token != null && completion.fcmTokenSaved) deviceTokens.saveLastSentToken(token)

        val auth = config.getAuth()
        if (PostLoginRules.needsProfile(completion, auth)) {
            // New account → auth/me has nothing yet; existing → prefill from it.
            val user = if (completion.registrationRequired) null else (account.me() as? AppResult.Success)?.data
            return AppResult.Success(
                PostLoginDestination.UserProfile(
                    user = user,
                    registrationRequired = completion.registrationRequired,
                    skippable = PostLoginRules.canSkip(completion.registrationRequired, auth),
                ),
            )
        }
        return when (val me = account.me()) {
            is AppResult.Success -> AppResult.Success(dashboardFor(me.data))
            is AppResult.Failure -> me
        }
    }

    private suspend fun dashboardFor(user: AccountUser): PostLoginDestination.Dashboard {
        val role = ActiveRole.resolve(account.getSavedActiveRole(), user)
        role?.let { account.saveActiveRole(it) }
        return PostLoginDestination.Dashboard(user, role)
    }
}

/** Login-screen consent waiting to be sent with the profile (null = nothing ticked). */
class GetLoginConsentUseCase @Inject constructor(private val session: SessionRepository) {
    suspend operator fun invoke(): LoginConsent? = session.getLoginConsent()?.takeIf { it.termsAccepted }
}

/** Result of pressing Save on the user profile screen. */
sealed interface SaveUserProfileResult {
    data class Saved(val user: AccountUser, val activeRole: UserRole?, val created: Boolean) : SaveUserProfileResult

    /**
     * Local or server (400 / 409) field problems. [message] = banner text, if any.
     * [UserProfileField.CONSENTS] in [fieldErrors] → Terms must be ticked on the login screen again.
     */
    data class Invalid(val fieldErrors: Map<UserProfileField, String>, val message: String? = null) :
        SaveUserProfileResult

    data class Failed(val error: AppError) : SaveUserProfileResult
}

class SaveUserProfileUseCase @Inject constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val config: ConfigRepository,
) {

    suspend operator fun invoke(
        form: UserProfileForm,
        registrationRequired: Boolean,
        today: SimpleDate = Companion.today(),
    ): SaveUserProfileResult {
        val errors = UserProfileValidator.validate(form, today)
        if (errors.isNotEmpty()) return SaveUserProfileResult.Invalid(errors)

        val consent = session.getLoginConsent()?.takeIf { it.termsAccepted }
        // The server refuses a new account without TERMS + PRIVACY → never send it.
        if (registrationRequired && consent == null) {
            return SaveUserProfileResult.Invalid(mapOf(UserProfileField.CONSENTS to CONSENT_MESSAGE), CONSENT_MESSAGE)
        }

        val request = buildRequest(form, registrationRequired, consent, config.getLegal())
        return when (val r = account.saveUserProfile(request)) {
            is AppResult.Success -> {
                val role = r.data.role ?: r.data.user.role ?: request.role
                role?.let { account.saveActiveRole(it) }
                session.clearLoginConsent() // recorded on the server now
                SaveUserProfileResult.Saved(r.data.user, role, r.data.created)
            }
            is AppResult.Failure -> when (val e = r.error) {
                is AppError.Validation -> {
                    val fields = e.missingFields.mapNotNull(UserProfileField::fromServer)
                    SaveUserProfileResult.Invalid(
                        fieldErrors = fields.associateWith { if (it == UserProfileField.CONSENTS) CONSENT_MESSAGE else "Required" },
                        message = if (UserProfileField.CONSENTS in fields) CONSENT_MESSAGE else e.message,
                    )
                }
                is AppError.EmailInUse -> SaveUserProfileResult.Invalid(mapOf(UserProfileField.EMAIL to e.message))
                else -> SaveUserProfileResult.Failed(e)
            }
        }
    }

    companion object {
        const val CONSENT_MESSAGE = "Please accept the Terms of Service and Privacy Policy on the login screen."

        /** Login-screen checkboxes → consents (config versions) + clientAcceptedAt. */
        fun consentsOf(consent: LoginConsent, legal: LegalSettings): List<ConsentItem> = listOf(
            ConsentItem(ConsentType.TERMS, legal.termsVersion, granted = true),
            ConsentItem(ConsentType.PRIVACY, legal.privacyVersion, granted = true),
            ConsentItem(ConsentType.WHATSAPP, version = "", granted = consent.whatsappOptIn),
        )

        fun buildRequest(
            form: UserProfileForm,
            registrationRequired: Boolean,
            consent: LoginConsent?,
            legal: LegalSettings,
        ) = UserProfileRequest(
            isProfileCompleted = true,
            role = form.role,
            fullName = form.fullName.trim(),
            gender = form.gender,
            dateOfBirth = form.dateOfBirth.trim(),
            city = form.city.trim(),
            state = form.state.trim(),
            email = form.email.trim().ifBlank { null },
            occupation = if (form.role == UserRole.TENANT) form.occupation.trim().ifBlank { null } else null,
            referralCode = if (registrationRequired) form.referralCode.trim().ifBlank { null } else null,
            consents = consent?.let { consentsOf(it, legal) },
            clientAcceptedAt = consent?.consentTimeMillis,
        )

        fun today(): SimpleDate = Calendar.getInstance().let {
            SimpleDate(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
        }
    }
}

/** Skip (existing account only): POST userProfile { isProfileCompleted:false, role: user.role } → dashboard. */
class SkipUserProfileUseCase @Inject constructor(private val account: AccountRepository) {
    suspend operator fun invoke(user: AccountUser?): AppResult<PostLoginDestination.Dashboard> {
        val request = UserProfileRequest(isProfileCompleted = false, role = user?.role)
        return when (val r = account.saveUserProfile(request)) {
            is AppResult.Success -> {
                val saved = r.data.user.takeIf { it.id.isNotBlank() } ?: user ?: r.data.user
                val role = ActiveRole.resolve(account.getSavedActiveRole(), saved)
                role?.let { account.saveActiveRole(it) }
                AppResult.Success(PostLoginDestination.Dashboard(saved, role))
            }
            is AppResult.Failure -> r
        }
    }
}

/** Role switcher on the dashboard (only shown when the account has more than one role). */
class SwitchActiveRoleUseCase @Inject constructor(private val account: AccountRepository) {
    suspend operator fun invoke(user: AccountUser, role: UserRole): UserRole? {
        if (role !in user.allRoles) return ActiveRole.resolve(account.getSavedActiveRole(), user)
        account.saveActiveRole(role)
        return role
    }
}

enum class FcmTokenSync { SENT, UNCHANGED, SIGNED_OUT, FAILED, REJECTED }

/**
 * FirebaseMessagingService.onNewToken → (WorkManager) → PUT devices/fcm-token,
 * only when signed in and the token differs from the last one the server has.
 * FAILED = retry later (network / 5xx); REJECTED = don't retry (4xx).
 */
class SyncFcmTokenUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val deviceTokens: DeviceTokenRepository,
) {
    suspend operator fun invoke(token: String?): FcmTokenSync {
        if (auth.currentUser() == null) return FcmTokenSync.SIGNED_OUT
        val current = token?.takeIf { it.isNotBlank() } ?: deviceTokens.currentToken() ?: return FcmTokenSync.FAILED
        if (current == deviceTokens.lastSentToken()) return FcmTokenSync.UNCHANGED
        return when (val r = account.registerFcmToken(current)) {
            is AppResult.Success -> {
                deviceTokens.saveLastSentToken(current)
                FcmTokenSync.SENT
            }
            is AppResult.Failure -> {
                val e = r.error
                val rejected = e is AppError.Validation ||
                    (e is AppError.Server && e.httpCode in 400..499 && e.httpCode != 408 && e.httpCode != 429)
                if (rejected) {
                    FcmTokenSync.REJECTED
                } else {
                    FcmTokenSync.FAILED
                }
            }
        }
    }
}
