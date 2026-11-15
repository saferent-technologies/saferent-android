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
    /** Counts per tab (GET properties/summary) → tabs with 0 are hidden. */
    val summary: com.saferenttechnologies.android.domain.model.PropertySummary? = null,
    /** Counts call failed → show All + the selected tab instead of waiting forever. */
    val countsFailed: Boolean = false,
) : UiState {
    /** Tabs appear all together once the counts are in (no "All" alone first, then the rest). */
    val tabsReady: Boolean get() = summary != null || countsFailed
    val hasMore: Boolean get() = page < totalPages
    /** Until the counts arrive: just All + the selected tab (no flash of empty tabs). */
    val filters: List<PropertyFilter>
        get() = summary?.visibleFilters(filter) ?: listOf(PropertyFilter.ALL, filter).distinct()
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

    /**
     * Loaded pages per tab (no search text). Opening the screen / switching to a loaded tab shows
     * these with NO API call; only pull-to-refresh or a change elsewhere (Invalidate) loads again.
     */
    private data class Cached(val items: List<PropertyListItem>, val page: Int, val totalPages: Int)
    private val cache = mutableMapOf<PropertyFilter, Cached>()
    /** Something changed while the screen was closed → next open loads fresh. */
    private var stale = false

    init {
        viewModelScope.launch {
            sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { MyPropertiesState() } }
        }
    }

    override suspend fun handleIntent(intent: MyPropertiesIntent) {
        when (intent) {
            is MyPropertiesIntent.Open -> {
                if (stale) {
                    cache.clear()
                    stale = false
                    setState { copy(summary = null) }
                }
                val counts = currentState.summary
                val cached = cache[intent.filter]
                setState {
                    MyPropertiesState(
                        isOpen = true,
                        filter = intent.filter,
                        summary = counts,
                        items = cached?.items.orEmpty(),
                        page = cached?.page ?: 0,
                        totalPages = cached?.totalPages ?: 1,
                    )
                }
                if (counts == null) loadCounts()
                if (cached == null) reload()
            }
            MyPropertiesIntent.Close -> setState { copy(isOpen = false) }
            is MyPropertiesIntent.SelectFilter -> {
                val cached = cache[intent.filter]?.takeIf { currentState.query.isBlank() }
                setState {
                    copy(filter = intent.filter, items = cached?.items.orEmpty(), page = cached?.page ?: 0, totalPages = cached?.totalPages ?: 1, error = null)
                }
                if (cached == null) reload() else loadJob?.cancel()
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
                com.saferenttechnologies.android.data.remote.ApiCache.clear()
                cache.clear()
                setState { copy(refreshing = true) }
                loadCounts()
                reload()
            }
            MyPropertiesIntent.LoadMore -> if (currentState.hasMore && !currentState.loading) load(currentState.page + 1)
            MyPropertiesIntent.Invalidate -> {
                cache.clear()
                if (currentState.isOpen) {
                    loadCounts()
                    reload()
                } else {
                    stale = true
                }
            }
        }
    }

    private fun loadCounts() {
        viewModelScope.launch {
            when (val r = repository.summary()) {
                is AppResult.Success -> setState { copy(summary = r.data, countsFailed = false) }
                is AppResult.Failure -> setState { copy(countsFailed = true) }
            }
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
            is AppResult.Success -> {
                setState {
                    copy(
                        items = if (page == 1) r.data.items else items + r.data.items,
                        page = r.data.page,
                        totalPages = r.data.totalPages,
                        loading = false,
                        refreshing = false,
                    )
                }
                // Remember this tab (plain list, no search) so coming back needs no call.
                if (state.query.isBlank() && currentState.filter == state.filter) {
                    cache[state.filter] = Cached(currentState.items, currentState.page, currentState.totalPages)
                }
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
