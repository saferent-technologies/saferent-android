package com.saferenttechnologies.android.presentation.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.BrowseFilters
import com.saferenttechnologies.android.domain.model.BrowseSort
import com.saferenttechnologies.android.domain.model.Listing
import com.saferenttechnologies.android.domain.model.LocationSuggestion
import com.saferenttechnologies.android.domain.model.NearMe
import com.saferenttechnologies.android.domain.model.PlaceFilter
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import com.saferenttechnologies.android.domain.repository.LocationProvider
import com.saferenttechnologies.android.domain.repository.PropertyRepository
import com.saferenttechnologies.android.domain.repository.RecentSearchStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quick chips under the search bar. */
enum class QuickChip { NEAR_ME, APARTMENT, HOUSE, PG, VILLA, COMMERCIAL, UNDER_10K, UNDER_20K, FURNISHED, BHK_2 }

/** A property type for the filter sheet (from GET property-types). */
data class TypeOption(val key: String, val label: String, val category: String)

data class ExploreState(
    val filters: BrowseFilters = BrowseFilters(),
    val items: List<Listing> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val total: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val appendFailed: Boolean = false,
    // filter / sort sheets
    val draft: BrowseFilters? = null,
    val draftCount: Int? = null,
    val sortOpen: Boolean = false,
    val types: List<TypeOption> = emptyList(),
    // search
    val searchOpen: Boolean = false,
    val searchText: String = "",
    val suggestions: List<LocationSuggestion> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val recent: List<String> = emptyList(),
    // near me
    val locating: Boolean = false,
) : UiState {
    val hasMore: Boolean get() = page < totalPages
    val searchLabel: String get() = filters.place?.label ?: filters.query
}

sealed interface ExploreIntent : UiIntent {
    data object Start : ExploreIntent
    data object Refresh : ExploreIntent
    data object LoadMore : ExploreIntent
    data object Retry : ExploreIntent
    data class ToggleChip(val chip: QuickChip) : ExploreIntent
    /** Quick pill for one of our property types (Apartment, PG …): on / off. */
    data class QuickType(val key: String) : ExploreIntent
    data object ClearAll : ExploreIntent

    data object OpenFilters : ExploreIntent
    data class DraftChanged(val filters: BrowseFilters) : ExploreIntent
    data object ResetDraft : ExploreIntent
    data object ApplyDraft : ExploreIntent
    data object CloseFilters : ExploreIntent

    data object OpenSort : ExploreIntent
    data object CloseSort : ExploreIntent
    data class SelectSort(val sort: BrowseSort) : ExploreIntent

    data object OpenSearch : ExploreIntent
    data object CloseSearch : ExploreIntent
    data class SearchTextChanged(val text: String) : ExploreIntent
    data class PickSuggestion(val suggestion: LocationSuggestion) : ExploreIntent
    data class SubmitSearch(val text: String) : ExploreIntent
    data object ClearRecent : ExploreIntent

    /** Location permission granted → one fix → "Near me". */
    data object NearMe : ExploreIntent
    data class SetRadius(val km: Int) : ExploreIntent
}

sealed interface ExploreEffect : UiEffect {
    data class Message(val key: String) : ExploreEffect
    data object ScrollToTop : ExploreEffect
}

/** Explore feed: search with place suggestions, quick chips, filters (live count), sort, near me, load-more paging. */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: BrowseRepository,
    private val properties: PropertyRepository,
    private val recentSearches: RecentSearchStore,
    private val location: LocationProvider,
    private val logger: AppLogger,
    private val savedState: SavedStateHandle,
    sessionEvents: SessionEvents,
) : MviViewModel<ExploreState, ExploreIntent, ExploreEffect>(ExploreState(filters = FiltersSaver.restore(savedState))) {

    private var loadJob: Job? = null
    private var countJob: Job? = null
    private var suggestJob: Job? = null
    private var started = false

    init {
        viewModelScope.launch { recentSearches.recent.collect { r -> setState { copy(recent = r) } } }
        viewModelScope.launch {
            sessionEvents.events.collect {
                if (it == SessionEvent.SignedOut) {
                    started = false
                    loadJob?.cancel()
                    FiltersSaver.save(savedState, BrowseFilters())
                    setState { ExploreState(recent = recent) }
                }
            }
        }
    }

    override suspend fun handleIntent(intent: ExploreIntent) {
        when (intent) {
            ExploreIntent.Start -> if (!started) {
                started = true
                load(reset = true)
                loadTypes()
            }
            ExploreIntent.Refresh -> { com.saferenttechnologies.android.data.remote.ApiCache.clear(); load(reset = true, refreshing = true) }
            ExploreIntent.Retry -> if (currentState.items.isEmpty()) load(reset = true) else loadMore()
            ExploreIntent.LoadMore -> loadMore()
            is ExploreIntent.ToggleChip -> toggle(intent.chip)
            is ExploreIntent.QuickType -> currentState.filters.let { f ->
                apply(f.copy(propertyType = if (f.propertyType == intent.key) null else intent.key))
            }
            ExploreIntent.ClearAll -> apply(BrowseFilters())

            ExploreIntent.OpenFilters -> {
                setState { copy(draft = filters, draftCount = total) }
                if (currentState.types.isEmpty()) loadTypes()
            }
            is ExploreIntent.DraftChanged -> {
                setState { copy(draft = intent.filters) }
                countDraft()
            }
            ExploreIntent.ResetDraft -> {
                // Keeps place / search / near me, resets the sheet's own fields.
                val f = currentState.filters
                setState { copy(draft = BrowseFilters(query = f.query, place = f.place, nearMe = f.nearMe, sort = f.sort)) }
                countDraft()
            }
            ExploreIntent.ApplyDraft -> {
                val d = currentState.draft ?: return
                setState { copy(draft = null, draftCount = null) }
                apply(d)
            }
            ExploreIntent.CloseFilters -> {
                countJob?.cancel()
                setState { copy(draft = null, draftCount = null) }
            }

            ExploreIntent.OpenSort -> setState { copy(sortOpen = true) }
            ExploreIntent.CloseSort -> setState { copy(sortOpen = false) }
            is ExploreIntent.SelectSort -> {
                setState { copy(sortOpen = false) }
                apply(currentState.filters.copy(sort = intent.sort))
            }

            ExploreIntent.OpenSearch -> setState { copy(searchOpen = true, searchText = filters.query, suggestions = emptyList()) }
            ExploreIntent.CloseSearch -> {
                suggestJob?.cancel()
                setState { copy(searchOpen = false, suggestionsLoading = false) }
            }
            is ExploreIntent.SearchTextChanged -> {
                setState { copy(searchText = intent.text) }
                suggest(intent.text)
            }
            is ExploreIntent.PickSuggestion -> {
                suggestJob?.cancel()
                recentSearches.add(intent.suggestion.label)
                setState { copy(searchOpen = false, suggestions = emptyList()) }
                apply(currentState.filters.copy(place = intent.suggestion.filter, query = "", nearMe = null, sort = sortWithoutNearest()))
            }
            is ExploreIntent.SubmitSearch -> {
                suggestJob?.cancel()
                val q = intent.text.trim()
                if (q.isNotEmpty()) recentSearches.add(q)
                setState { copy(searchOpen = false, suggestions = emptyList()) }
                apply(currentState.filters.copy(query = q, place = null, nearMe = null, sort = sortWithoutNearest()))
            }
            ExploreIntent.ClearRecent -> recentSearches.clear()

            ExploreIntent.NearMe -> nearMe()
            is ExploreIntent.SetRadius -> currentState.filters.nearMe?.let { apply(currentState.filters.copy(nearMe = it.copy(radiusKm = intent.km))) }
        }
    }

    // ---------------- feed ----------------

    private fun apply(filters: BrowseFilters) {
        FiltersSaver.save(savedState, filters)
        setState { copy(filters = filters) }
        sendEffect(ExploreEffect.ScrollToTop)
        load(reset = true)
    }

    private fun load(reset: Boolean, refreshing: Boolean = false) {
        loadJob?.cancel()
        val filters = currentState.filters
        setState {
            copy(
                loading = !refreshing && reset,
                refreshing = refreshing,
                error = null,
                appendFailed = false,
                items = if (reset && !refreshing) emptyList() else items,
            )
        }
        loadJob = viewModelScope.launch {
            when (val r = repository.browse(filters, page = 1, pageSize = PAGE_SIZE)) {
                is AppResult.Success -> setState {
                    copy(items = r.data.items, page = r.data.page, totalPages = r.data.totalPages, total = r.data.total, loading = false, refreshing = false, offline = false)
                }
                is AppResult.Failure -> setState {
                    copy(
                        loading = false,
                        refreshing = false,
                        offline = r.error is AppError.Network,
                        error = r.error.takeUnless { it is AppError.AccountBlocked }?.message,
                    )
                }
            }
        }
    }

    private fun loadMore() {
        val s = currentState
        if (!s.hasMore || s.loading || s.loadingMore || loadJob?.isActive == true) return
        val filters = s.filters
        setState { copy(loadingMore = true, appendFailed = false) }
        loadJob = viewModelScope.launch {
            when (val r = repository.browse(filters, page = s.page + 1, pageSize = PAGE_SIZE)) {
                is AppResult.Success -> setState {
                    copy(items = (items + r.data.items).distinctBy { it.id }, page = r.data.page, totalPages = r.data.totalPages, total = r.data.total, loadingMore = false)
                }
                is AppResult.Failure -> setState { copy(loadingMore = false, appendFailed = true, offline = r.error is AppError.Network) }
            }
        }
    }

    private suspend fun loadTypes() {
        (properties.propertyTypes() as? AppResult.Success)?.data?.let { t ->
            setState {
                copy(types = t.categories.flatMap { c -> c.types.filter { it.enabled }.map { TypeOption(it.key, it.label, c.key) } })
            }
        }
    }

    /** Live "Show n homes": pageSize 1, debounced 400 ms. */
    private fun countDraft() {
        countJob?.cancel()
        val draft = currentState.draft ?: return
        setState { copy(draftCount = null) }
        countJob = viewModelScope.launch {
            delay(COUNT_DEBOUNCE_MS)
            (repository.browse(draft, page = 1, pageSize = 1) as? AppResult.Success)?.let { r -> setState { copy(draftCount = r.data.total) } }
        }
    }

    // ---------------- chips ----------------

    fun isActive(chip: QuickChip, f: BrowseFilters = currentState.filters): Boolean = QuickChips.isActive(chip, f, currentState.types)

    private fun toggle(chip: QuickChip) {
        if (chip == QuickChip.NEAR_ME) {
            if (currentState.filters.nearMe != null) {
                apply(currentState.filters.copy(nearMe = null, sort = sortWithoutNearest()))
            }
            // Turning it on needs the permission → the screen asks, then sends NearMe.
            return
        }
        apply(QuickChips.toggle(chip, currentState.filters, currentState.types))
    }

    private fun sortWithoutNearest(): BrowseSort =
        currentState.filters.sort.takeUnless { it == BrowseSort.NEAREST } ?: BrowseSort.NEWEST

    // ---------------- search ----------------

    private fun suggest(text: String) {
        suggestJob?.cancel()
        if (text.isBlank()) {
            setState { copy(suggestions = emptyList(), suggestionsLoading = false) }
            return
        }
        setState { copy(suggestionsLoading = true) }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val r = repository.locations(text.trim())
            setState { copy(suggestions = (r as? AppResult.Success)?.data.orEmpty(), suggestionsLoading = false) }
        }
    }

    // ---------------- near me ----------------

    private suspend fun nearMe() {
        if (currentState.locating) return
        setState { copy(locating = true, searchOpen = false) }
        val fix = location.currentFix()
        setState { copy(locating = false) }
        if (fix == null) {
            logger.d(TAG, "near me: no location fix")
            sendEffect(ExploreEffect.Message(MESSAGE_NO_FIX))
            return
        }
        val radius = currentState.filters.nearMe?.radiusKm ?: DEFAULT_RADIUS_KM
        apply(currentState.filters.copy(nearMe = NearMe(fix.latitude, fix.longitude, radius), place = null, query = "", sort = BrowseSort.NEAREST))
    }

    companion object {
        const val PAGE_SIZE = 12
        const val DEFAULT_RADIUS_KM = 10
        val RADIUS_OPTIONS = listOf(2, 5, 10, 25, 50)
        const val MESSAGE_NO_FIX = "no_fix"
        private const val COUNT_DEBOUNCE_MS = 400L
        private const val SUGGEST_DEBOUNCE_MS = 250L
        private const val TAG = "SR-Explore"
    }
}

/** Quick chip ↔ filters (pure, unit-tested). Property type keys are matched against GET property-types. */
object QuickChips {
    private fun typeKey(chip: QuickChip, types: List<TypeOption>): String? {
        val (fallback, words) = when (chip) {
            QuickChip.APARTMENT -> "APARTMENT" to listOf("APARTMENT", "FLAT")
            QuickChip.HOUSE -> "INDEPENDENT_HOUSE" to listOf("INDEPENDENT", "HOUSE")
            QuickChip.PG -> "PG" to listOf("PG", "HOSTEL")
            QuickChip.VILLA -> "VILLA" to listOf("VILLA")
            else -> return null
        }
        return types.firstOrNull { t -> words.any { w -> t.key.contains(w, true) || t.label.contains(w, true) } }?.key ?: fallback
    }

    fun isActive(chip: QuickChip, f: BrowseFilters, types: List<TypeOption>): Boolean = when (chip) {
        QuickChip.NEAR_ME -> f.nearMe != null
        QuickChip.APARTMENT, QuickChip.HOUSE, QuickChip.PG, QuickChip.VILLA -> f.propertyType != null && f.propertyType == typeKey(chip, types)
        QuickChip.COMMERCIAL -> f.category == "COMMERCIAL"
        QuickChip.UNDER_10K -> f.maxRent == 10_000
        QuickChip.UNDER_20K -> f.maxRent == 20_000
        QuickChip.FURNISHED -> f.furnishing == "FULLY_FURNISHED"
        QuickChip.BHK_2 -> f.bedrooms == 2
    }

    fun toggle(chip: QuickChip, f: BrowseFilters, types: List<TypeOption>): BrowseFilters {
        val on = isActive(chip, f, types)
        return when (chip) {
            QuickChip.NEAR_ME -> f
            QuickChip.APARTMENT, QuickChip.HOUSE, QuickChip.PG, QuickChip.VILLA -> f.copy(propertyType = if (on) null else typeKey(chip, types))
            QuickChip.COMMERCIAL -> f.copy(category = if (on) null else "COMMERCIAL")
            QuickChip.UNDER_10K -> f.copy(maxRent = if (on) null else 10_000)
            QuickChip.UNDER_20K -> f.copy(maxRent = if (on) null else 20_000)
            QuickChip.FURNISHED -> f.copy(furnishing = if (on) null else "FULLY_FURNISHED")
            QuickChip.BHK_2 -> f.copy(bedrooms = if (on) null else 2)
        }
    }
}

/** Filters ↔ SavedStateHandle (survive rotation + process death). Only bundle-safe values. */
object FiltersSaver {
    private const val P = "explore."

    fun save(h: SavedStateHandle, f: BrowseFilters) {
        h["${P}q"] = f.query
        h["${P}placeLabel"] = f.place?.label
        h["${P}placeCity"] = f.place?.city
        h["${P}placeLocality"] = f.place?.locality
        h["${P}placeState"] = f.place?.state
        h["${P}lat"] = f.nearMe?.latitude
        h["${P}lng"] = f.nearMe?.longitude
        h["${P}radius"] = f.nearMe?.radiusKm
        h["${P}listingType"] = f.listingType
        h["${P}category"] = f.category
        h["${P}propertyType"] = f.propertyType
        h["${P}minRent"] = f.minRent
        h["${P}maxRent"] = f.maxRent
        h["${P}bedrooms"] = f.bedrooms
        h["${P}furnishing"] = f.furnishing
        h["${P}availableOnly"] = f.availableOnly
        h["${P}sort"] = f.sort.key
    }

    fun restore(h: SavedStateHandle): BrowseFilters {
        val label: String? = h["${P}placeLabel"]
        val lat: Double? = h["${P}lat"]
        val lng: Double? = h["${P}lng"]
        return BrowseFilters(
            query = h["${P}q"] ?: "",
            place = label?.let { PlaceFilter(it, h["${P}placeCity"], h["${P}placeLocality"], h["${P}placeState"]) },
            nearMe = if (lat != null && lng != null) NearMe(lat, lng, h["${P}radius"] ?: ExploreViewModel.DEFAULT_RADIUS_KM) else null,
            // An old saved "SALE" filter is dropped (rentals only).
            listingType = h.get<String>("${P}listingType")?.takeIf { it.uppercase() in com.saferenttechnologies.android.domain.model.ListingTypes.RENTAL },
            category = h["${P}category"],
            propertyType = h["${P}propertyType"],
            minRent = h["${P}minRent"],
            maxRent = h["${P}maxRent"],
            bedrooms = h["${P}bedrooms"],
            furnishing = h["${P}furnishing"],
            availableOnly = h["${P}availableOnly"] ?: true,
            sort = BrowseSort.of(h["${P}sort"]),
        )
    }
}
