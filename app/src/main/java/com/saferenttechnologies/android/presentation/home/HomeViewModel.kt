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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val user: AccountUser? = null,
    val activeRole: UserRole? = null,
    val isSigningOut: Boolean = false,
) : UiState {
    /** Role switcher only when the account has more than one role. */
    val showRoleSwitcher: Boolean get() = (user?.allRoles?.size ?: 0) > 1
    val roles: List<UserRole> get() = user?.allRoles.orEmpty()
}

sealed interface HomeIntent : UiIntent {
    /** Open the dashboard for this user + active role. */
    data class Open(val dashboard: PostLoginDestination.Dashboard) : HomeIntent
    data class SwitchRole(val role: UserRole) : HomeIntent
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
    sessionEvents: SessionEvents,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        viewModelScope.launch {
            sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { HomeState() } }
        }
    }

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.Open -> setState {
                copy(user = intent.dashboard.user, activeRole = intent.dashboard.activeRole, isSigningOut = false)
            }
            is HomeIntent.SwitchRole -> {
                val user = currentState.user ?: return
                val role = switchActiveRole(user, intent.role)
                setState { copy(activeRole = role) }
            }
            // POST auth/logout { fcmToken } → Firebase sign-out → SignedOut resets every screen.
            HomeIntent.SignOut -> if (!currentState.isSigningOut) {
                setState { copy(isSigningOut = true) }
                signOut()
            }
        }
    }
}
