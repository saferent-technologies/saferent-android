package com.saferenttechnologies.android.presentation.manage

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.PropertyFilter
import com.saferenttechnologies.android.domain.model.PropertyListItem
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyPropertiesState(
    val isOpen: Boolean = false,
    val filter: PropertyFilter = PropertyFilter.ALL,
    val query: String = "",
    val items: List<PropertyListItem> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
) : UiState {
    val hasMore: Boolean get() = page < totalPages
}

sealed interface MyPropertiesIntent : UiIntent {
    data class Open(val filter: PropertyFilter = PropertyFilter.ALL) : MyPropertiesIntent
    data object Close : MyPropertiesIntent
    data class SelectFilter(val filter: PropertyFilter) : MyPropertiesIntent
    data class QueryChanged(val query: String) : MyPropertiesIntent
    data object Refresh : MyPropertiesIntent
    data object LoadMore : MyPropertiesIntent
    /** Something changed elsewhere (action / edit) → reload when shown. */
    data object Invalidate : MyPropertiesIntent
}

sealed interface MyPropertiesEffect : UiEffect

/** "My properties": tabs, search (debounced 350 ms), pull-to-refresh, load-more paging. */
@HiltViewModel
class MyPropertiesViewModel @Inject constructor(
    private val repository: ManagePropertyRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<MyPropertiesState, MyPropertiesIntent, MyPropertiesEffect>(MyPropertiesState()) {

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { MyPropertiesState() } }
        }
    }

    override suspend fun handleIntent(intent: MyPropertiesIntent) {
        when (intent) {
            is MyPropertiesIntent.Open -> {
                setState { MyPropertiesState(isOpen = true, filter = intent.filter) }
                reload()
            }
            MyPropertiesIntent.Close -> setState { copy(isOpen = false) }
            is MyPropertiesIntent.SelectFilter -> {
                setState { copy(filter = intent.filter, items = emptyList(), page = 0, totalPages = 1) }
                reload()
            }
            is MyPropertiesIntent.QueryChanged -> {
                setState { copy(query = intent.query) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    reload()
                }
            }
            MyPropertiesIntent.Refresh -> {
                setState { copy(refreshing = true) }
                reload()
            }
            MyPropertiesIntent.LoadMore -> if (currentState.hasMore && !currentState.loading) load(currentState.page + 1)
            MyPropertiesIntent.Invalidate -> if (currentState.isOpen) reload()
        }
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load(1) }
    }

    private suspend fun load(page: Int) {
        val state = currentState
        setState { copy(loading = true, error = null) }
        when (val r = repository.list(state.filter, state.query, page)) {
            is AppResult.Success -> setState {
                copy(
                    items = if (page == 1) r.data.items else items + r.data.items,
                    page = r.data.page,
                    totalPages = r.data.totalPages,
                    loading = false,
                    refreshing = false,
                )
            }
            is AppResult.Failure -> setState {
                copy(loading = false, refreshing = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message)
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
