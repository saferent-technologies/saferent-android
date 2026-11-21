package com.saferenttechnologies.android.presentation.tenantcheck

import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.PeopleLookup
import com.saferenttechnologies.android.domain.model.TenantReport
import com.saferenttechnologies.android.domain.repository.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Landlord → "Review tenant": type a phone or SafeRent ID → earlier landlords' reviews + rent record. */
data class TenantCheckState(
    val isOpen: Boolean = false,
    val query: String = "",
    val invalid: Boolean = false,
    val loading: Boolean = false,
    val report: TenantReport? = null,
    val notFound: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface TenantCheckIntent : UiIntent {
    data object Open : TenantCheckIntent
    data object Close : TenantCheckIntent
    data class Query(val text: String) : TenantCheckIntent
    data object Search : TenantCheckIntent
}

sealed interface TenantCheckEffect : UiEffect

@HiltViewModel
class TenantCheckViewModel @Inject constructor(
    private val people: PeopleRepository,
) : MviViewModel<TenantCheckState, TenantCheckIntent, TenantCheckEffect>(TenantCheckState()) {

    override suspend fun handleIntent(intent: TenantCheckIntent) {
        when (intent) {
            TenantCheckIntent.Open -> setState { TenantCheckState(isOpen = true) }
            TenantCheckIntent.Close -> setState { TenantCheckState() }
            is TenantCheckIntent.Query -> setState { copy(query = com.saferenttechnologies.android.domain.model.PeopleLookup.cleanInput(intent.text), invalid = false) }
            TenantCheckIntent.Search -> search()
        }
    }

    private suspend fun search() {
        if (currentState.loading) return
        val q = PeopleLookup.parse(currentState.query) ?: return setState { copy(invalid = true) }
        setState { copy(loading = true, report = null, notFound = false, error = null) }
        when (val r = people.tenantReport(q)) {
            is AppResult.Success -> setState { copy(loading = false, report = r.data, notFound = r.data == null) }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }
}
