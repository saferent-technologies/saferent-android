package com.saferenttechnologies.android.presentation.profile

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.UserProfileField
import com.saferenttechnologies.android.domain.model.UserProfileForm
import com.saferenttechnologies.android.domain.model.PostLoginDestination
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.usecase.SaveUserProfileResult
import com.saferenttechnologies.android.domain.usecase.SaveUserProfileUseCase
import com.saferenttechnologies.android.domain.usecase.GetLoginConsentUseCase
import com.saferenttechnologies.android.domain.usecase.SignOutUseCase
import com.saferenttechnologies.android.domain.usecase.SkipUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Profile form after sign-in: POST userProfile (+ login consents) → dashboard of the returned role. */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val saveUserProfile: SaveUserProfileUseCase,
    private val skipUserProfile: SkipUserProfileUseCase,
    private val getLoginConsent: GetLoginConsentUseCase,
    private val signOut: SignOutUseCase,
    sessionEvents: SessionEvents,
) : MviViewModel<UserProfileState, UserProfileIntent, UserProfileEffect>(UserProfileState()) {

    /** Overridable in tests. */
    internal var today: () -> SimpleDate = { SaveUserProfileUseCase.today() }

    init {
        viewModelScope.launch {
            sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { UserProfileState() } }
        }
    }

    override suspend fun handleIntent(intent: UserProfileIntent) {
        when (intent) {
            is UserProfileIntent.Start -> {
                // A new account can't be created without Terms + Privacy from the login screen.
                val consentMissing = intent.destination.registrationRequired && getLoginConsent() == null
                setState {
                    UserProfileState(
                        isStarted = true,
                        form = UserProfileForm.from(intent.destination.user),
                        user = intent.destination.user,
                        registrationRequired = intent.destination.registrationRequired,
                        skippable = intent.destination.skippable,
                        consentMissing = consentMissing,
                        banner = if (consentMissing) SaveUserProfileUseCase.CONSENT_MESSAGE else null,
                    )
                }
            }
            is UserProfileIntent.NameChanged -> edit(UserProfileField.FULL_NAME) { copy(fullName = intent.value) }
            is UserProfileIntent.RoleSelected -> edit(UserProfileField.ROLE) { copy(role = intent.role) }
            is UserProfileIntent.GenderSelected -> edit(UserProfileField.GENDER) { copy(gender = intent.gender) }
            UserProfileIntent.OpenDatePicker -> setState { copy(showDatePicker = true) }
            UserProfileIntent.DismissDatePicker -> setState { copy(showDatePicker = false) }
            is UserProfileIntent.DateOfBirthPicked -> {
                setState { copy(showDatePicker = false) }
                edit(UserProfileField.DATE_OF_BIRTH) { copy(dateOfBirth = intent.iso) }
            }
            is UserProfileIntent.CityChanged -> edit(UserProfileField.CITY) { copy(city = intent.value) }
            is UserProfileIntent.StateChanged -> edit(UserProfileField.STATE) { copy(state = intent.value) }
            is UserProfileIntent.EmailChanged -> edit(UserProfileField.EMAIL) { copy(email = intent.value) }
            is UserProfileIntent.OccupationChanged -> edit(UserProfileField.OCCUPATION) { copy(occupation = intent.value) }
            is UserProfileIntent.ReferralChanged -> edit(UserProfileField.REFERRAL_CODE) { copy(referralCode = intent.value) }
            UserProfileIntent.Save -> save()
            UserProfileIntent.Skip -> skip()
            UserProfileIntent.BackToLogin -> signOut() // SignedOut resets every screen → login
        }
    }

    private fun edit(field: UserProfileField, change: UserProfileForm.() -> UserProfileForm) =
        setState { copy(form = form.change(), fieldErrors = fieldErrors - field, banner = if (consentMissing) banner else null) }

    private suspend fun save() {
        val state = currentState
        if (state.busy) return
        if (state.consentMissing) return setState { copy(banner = SaveUserProfileUseCase.CONSENT_MESSAGE) }
        setState { copy(isSaving = true, banner = null) }
        when (val r = saveUserProfile(state.form, state.registrationRequired, today())) {
            is SaveUserProfileResult.Saved -> setState {
                copy(isSaving = false, dashboard = PostLoginDestination.Dashboard(r.user, r.activeRole))
            }
            is SaveUserProfileResult.Invalid -> setState {
                copy(
                    isSaving = false,
                    fieldErrors = r.fieldErrors,
                    banner = r.message,
                    consentMissing = UserProfileField.CONSENTS in r.fieldErrors,
                )
            }
            is SaveUserProfileResult.Failed -> setState { copy(isSaving = false, banner = r.error.bannerText()) }
        }
    }

    private suspend fun skip() {
        val state = currentState
        if (state.busy || !state.skippable || state.registrationRequired) return
        setState { copy(isSkipping = true, banner = null) }
        when (val r = skipUserProfile(state.user)) {
            is AppResult.Success -> setState { copy(isSkipping = false, dashboard = r.data) }
            is AppResult.Failure -> setState { copy(isSkipping = false, banner = r.error.bannerText()) }
        }
    }

    private fun AppError.bannerText(): String = when (this) {
        is AppError.LoginMethodDisabled -> "Phone sign-in is turned off right now"
        else -> message
    }
}
