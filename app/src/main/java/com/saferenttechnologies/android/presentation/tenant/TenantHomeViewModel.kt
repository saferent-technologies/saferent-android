package com.saferenttechnologies.android.presentation.tenant

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.TenantApplication
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import com.saferenttechnologies.android.domain.usecase.GetTenantHomeStateUseCase
import com.saferenttechnologies.android.domain.usecase.TenantHomeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TenantHomeUiState(
    val loading: Boolean = false,
    val home: TenantHomeState? = null,
    val error: String? = null,
    /** Rentals waiting for a review (blocks applying until done). */
    val pendingReviews: Int = 0,
    /** Linked tenant tapped "Explore homes". */
    val exploring: Boolean = false,
    val hasApplications: Boolean = false,
) : UiState {
    val showExplore: Boolean get() = home == TenantHomeState.Explore || exploring
}

sealed interface TenantHomeIntent : UiIntent {
    /** Tenant dashboard shown — loads once per session. */
    data object Start : TenantHomeIntent
    data object Refresh : TenantHomeIntent
    data object OpenExplore : TenantHomeIntent
    data object CloseExplore : TenantHomeIntent
}

sealed interface TenantHomeEffect : UiEffect

/** Tenant start: active lease → dashboard placeholder; none → Explore. Cached for the session. */
@HiltViewModel
class TenantHomeViewModel @Inject constructor(
    private val getHome: GetTenantHomeStateUseCase,
    private val repository: BrowseRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<TenantHomeUiState, TenantHomeIntent, TenantHomeEffect>(TenantHomeUiState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { TenantHomeUiState() } } }
    }

    override suspend fun handleIntent(intent: TenantHomeIntent) {
        when (intent) {
            TenantHomeIntent.Start -> if (currentState.home == null && !currentState.loading) load()
            TenantHomeIntent.Refresh -> load()
            TenantHomeIntent.OpenExplore -> setState { copy(exploring = true) }
            TenantHomeIntent.CloseExplore -> setState { copy(exploring = false) }
        }
    }

    private suspend fun load() {
        setState { copy(loading = true, error = null) }
        viewModelScope.launch {
            (repository.pendingReviews() as? AppResult.Success)?.let { r -> setState { copy(pendingReviews = r.data) } }
        }
        viewModelScope.launch {
            val any = listOf("PENDING", "APPROVED").any { s ->
                ((repository.myApplications(s) as? AppResult.Success<List<TenantApplication>>)?.data?.isNotEmpty() == true)
            }
            setState { copy(hasApplications = any) }
        }
        when (val r = getHome()) {
            is AppResult.Success -> setState { copy(loading = false, home = r.data) }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }
}
