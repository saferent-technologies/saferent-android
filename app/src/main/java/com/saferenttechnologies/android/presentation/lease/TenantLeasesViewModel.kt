package com.saferenttechnologies.android.presentation.lease

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.LeaseSummary
import com.saferenttechnologies.android.domain.model.TenantLeases
import com.saferenttechnologies.android.domain.repository.LeaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TenantLeasesState(
    val isOpen: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val active: LeaseSummary? = null,
    val past: List<LeaseSummary> = emptyList(),
    val error: String? = null,
) : UiState

sealed interface TenantLeasesIntent : UiIntent {
    data object Open : TenantLeasesIntent
    data object Close : TenantLeasesIntent
    data object Refresh : TenantLeasesIntent
}

sealed interface TenantLeasesEffect : UiEffect

/** Tenant "My leases": active on top, ended ones below; each opens the lease screen. */
@HiltViewModel
class TenantLeasesViewModel @Inject constructor(
    private val leases: LeaseRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<TenantLeasesState, TenantLeasesIntent, TenantLeasesEffect>(TenantLeasesState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { TenantLeasesState() } } }
    }

    override suspend fun handleIntent(intent: TenantLeasesIntent) {
        when (intent) {
            TenantLeasesIntent.Open -> {
                setState { copy(isOpen = true, loading = active == null && past.isEmpty()) }
                load(refreshing = false)
            }
            TenantLeasesIntent.Close -> setState { copy(isOpen = false) }
            TenantLeasesIntent.Refresh -> {
                com.saferenttechnologies.android.data.remote.ApiCache.clear()
                load(refreshing = true)
            }
        }
    }

    private suspend fun load(refreshing: Boolean) {
        setState { copy(refreshing = refreshing, error = null) }
        when (val r = leases.myLeases()) {
            is AppResult.Success -> {
                val (active, past) = TenantLeases.split(r.data)
                setState { copy(active = active, past = past, loading = false, refreshing = false) }
            }
            is AppResult.Failure -> setState { copy(loading = false, refreshing = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }
}
