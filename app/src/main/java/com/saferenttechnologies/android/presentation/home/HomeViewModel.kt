package com.saferenttechnologies.android.presentation.home

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.PostLoginDestination
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.domain.usecase.SignOutUseCase
import com.saferenttechnologies.android.domain.usecase.SwitchActiveRoleUseCase
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.domain.usecase.SyncFcmTokenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.PropertySummary
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val user: AccountUser? = null,
    val activeRole: UserRole? = null,
    val isSigningOut: Boolean = false,
    /** Landlord dashboard numbers (GET properties/summary). */
    val summary: PropertySummary? = null,
    /** First summary load → the dashboard shimmers. */
    val summaryLoading: Boolean = false,
) : UiState {
    /** Role switcher only when the account has more than one role. */
    val showRoleSwitcher: Boolean get() = (user?.allRoles?.size ?: 0) > 1
    val roles: List<UserRole> get() = user?.allRoles.orEmpty()
}

sealed interface HomeIntent : UiIntent {
    /** Open the dashboard for this user + active role. */
    data class Open(val dashboard: PostLoginDestination.Dashboard) : HomeIntent
    data class SwitchRole(val role: UserRole) : HomeIntent

    /** Dashboard shown / resumed → make sure the server has this phone's current FCM token. */
    data object Visible : HomeIntent

    /** A property changed (action / edit) → reload the landlord numbers. */
    data object RefreshSummary : HomeIntent
    data object SignOut : HomeIntent
}

sealed interface HomeEffect : UiEffect

/**
 * Dashboard placeholder per active role. The real dashboard (GET api/v1/dashboard?role=)
 * comes once its response format + design are final.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val switchActiveRole: SwitchActiveRoleUseCase,
    private val signOut: SignOutUseCase,
    private val syncFcmToken: SyncFcmTokenUseCase,
    private val manageProperties: ManagePropertyRepository,
    private val logger: AppLogger,
    sessionEvents: SessionEvents,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    private var tokenJob: Job? = null

    init {
        viewModelScope.launch {
            sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { HomeState() } }
        }
    }

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.Open -> {
                setState { copy(user = intent.dashboard.user, activeRole = intent.dashboard.activeRole, isSigningOut = false) }
                loadSummary()
            }
            // Firebase token vs the one saved locally after the last successful PUT:
            // same → nothing; changed → PUT devices/fcm-token, then saved locally.
            HomeIntent.Visible -> {
                if (tokenJob?.isActive != true) {
                    tokenJob = viewModelScope.launch {
                        logger.d(TAG, "FCM token check on dashboard → ${syncFcmToken(token = null)}")
                    }
                }
                loadSummary()
            }
            HomeIntent.RefreshSummary -> loadSummary()
            is HomeIntent.SwitchRole -> {
                val user = currentState.user ?: return
                val role = switchActiveRole(user, intent.role)
                setState { copy(activeRole = role) }
                loadSummary()
            }
            // POST auth/logout { fcmToken } → Firebase sign-out → SignedOut resets every screen.
            HomeIntent.SignOut -> if (!currentState.isSigningOut) {
                setState { copy(isSigningOut = true) }
                signOut()
            }
        }
    }

    /** Landlords only: GET properties/summary (kept as is when the call fails). */
    private fun loadSummary() {
        if (currentState.activeRole != UserRole.LANDLORD) return
        viewModelScope.launch {
            setState { copy(summaryLoading = true) }
            (manageProperties.summary() as? AppResult.Success)?.let { r -> setState { copy(summary = r.data) } }
            setState { copy(summaryLoading = false) }
        }
    }
}

private const val TAG = "SafeRent-Push"
