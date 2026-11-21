package com.saferenttechnologies.android.presentation.profile

import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.UserProfileField
import com.saferenttechnologies.android.domain.model.UserProfileForm
import com.saferenttechnologies.android.domain.model.Gender
import com.saferenttechnologies.android.domain.model.PostLoginDestination
import com.saferenttechnologies.android.domain.model.UserRole

data class UserProfileState(
    val isStarted: Boolean = false,
    val form: UserProfileForm = UserProfileForm(),
    /** Prefill user from auth/me (null for a new account). */
    val user: AccountUser? = null,
    val registrationRequired: Boolean = false,
    val skippable: Boolean = false,
    val fieldErrors: Map<UserProfileField, String> = emptyMap(),
    val banner: String? = null,
    /** New account but no Terms / Privacy from the login screen → Save disabled, go back to login. */
    val consentMissing: Boolean = false,
    val isSaving: Boolean = false,
    val isSkipping: Boolean = false,
    val showDatePicker: Boolean = false,
    /** Getting city / state from the phone's location. */
    val locating: Boolean = false,
    /** Location tried once (auto) → don't ask again on every recomposition. */
    val locationTried: Boolean = false,
    /** Set when done → open this dashboard. */
    val dashboard: PostLoginDestination.Dashboard? = null,
    /** Opened from the Profile tab card: edit my details (no role choice, no referral, back button). */
    val editing: Boolean = false,
    /** Verified user → the name is shown but can't be changed. */
    val nameLocked: Boolean = false,
    /** Edit saved → the updated user (host refreshes the dashboard and closes). */
    val saved: AccountUser? = null,
) : UiState {
    val busy: Boolean get() = isSaving || isSkipping
    val canSave: Boolean get() = !busy && !consentMissing
    val showOccupation: Boolean get() = form.role == UserRole.TENANT
    val showReferral: Boolean get() = registrationRequired && !editing
}

sealed interface UserProfileIntent : UiIntent {
    data class Start(val destination: PostLoginDestination.UserProfile) : UserProfileIntent
    data class NameChanged(val value: String) : UserProfileIntent
    data class RoleSelected(val role: UserRole) : UserProfileIntent
    data class GenderSelected(val gender: Gender) : UserProfileIntent
    data object OpenDatePicker : UserProfileIntent
    data object DismissDatePicker : UserProfileIntent
    /** "YYYY-MM-DD" */
    data class DateOfBirthPicked(val iso: String) : UserProfileIntent
    data class CityChanged(val value: String) : UserProfileIntent
    data class StateChanged(val value: String) : UserProfileIntent
    data class EmailChanged(val value: String) : UserProfileIntent
    data class OccupationChanged(val value: String) : UserProfileIntent
    data class ReferralChanged(val value: String) : UserProfileIntent
    /** Consent missing → sign out and tick Terms on the login screen again. */
    data object BackToLogin : UserProfileIntent
    data object Save : UserProfileIntent
    data object Skip : UserProfileIntent
    /** Location allowed → fill city + state from the phone's location (device Geocoder). */
    data object UseMyLocation : UserProfileIntent
    /** Location permission refused → type city / state by hand. */
    data object LocationDenied : UserProfileIntent
    /** Profile tab → Edit profile with the current details. [activeRole] is kept (role switch is on the Profile tab). */
    data class StartEdit(val user: AccountUser, val activeRole: UserRole?, val nameLocked: Boolean) : UserProfileIntent
    data object CloseEdit : UserProfileIntent
}

sealed interface UserProfileEffect : UiEffect
