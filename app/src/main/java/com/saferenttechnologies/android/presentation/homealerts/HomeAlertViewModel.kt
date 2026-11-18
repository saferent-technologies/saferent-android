package com.saferenttechnologies.android.presentation.homealerts

import com.saferenttechnologies.android.domain.model.LocationSuggestion
import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.HomeAlert
import com.saferenttechnologies.android.domain.model.HomeAlertRules
import com.saferenttechnologies.android.domain.repository.LocationProvider
import com.saferenttechnologies.android.domain.repository.PropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertTypeOption(val key: String, val label: String)

data class HomeAlertState(
    /** Sheet showing. */
    val isOpen: Boolean = false,
    /** What the server has (drives the "On / Off" badges elsewhere); null = not loaded yet. */
    val saved: HomeAlert? = null,
    /** Being edited in the sheet. */
    val draft: HomeAlert = HomeAlert(),
    val types: List<AlertTypeOption> = emptyList(),
    val loading: Boolean = false,
    val locating: Boolean = false,
    /** Typed area being looked up / not found. */
    val areaSearching: Boolean = false,
    val areaNotFound: Boolean = false,
    /** Our locations matching what's typed (areas / cities). */
    val suggestions: List<LocationSuggestion> = emptyList(),
    /** Full-screen area search (same look as Explore search). */
    val searchOpen: Boolean = false,
    val searchText: String = "",
    val saving: Boolean = false,
    val showErrors: Boolean = false,
    /** Tenant has an active lease (not in notice) → turning alerts on asks first. */
    val needsConfirm: Boolean = false,
    /** "Turn on while you have an active lease?" dialog; value = turned on from the card (true) or the sheet (false). */
    val confirmOn: Boolean? = null,
) : UiState {
    val changed: Boolean get() = saved == null || draft != saved
    val canSave: Boolean get() = !saving && !locating && !(areaSearching && !searchOpen) && changed && HomeAlertRules.canSave(draft)
}

sealed interface HomeAlertIntent : UiIntent {
    /** Load the saved setting (for the On / Off badge) without opening. */
    data object Load : HomeAlertIntent
    data object Open : HomeAlertIntent
    data object Close : HomeAlertIntent
    data class Enabled(val on: Boolean) : HomeAlertIntent
    /** Location permission granted → take a fix + area name. */
    data object UseMyLocation : HomeAlertIntent
    data object LocationDenied : HomeAlertIntent
    /** Area typed by hand → looked up (device Geocoder) after a short pause. */
    data object OpenSearch : HomeAlertIntent
    data object CloseSearch : HomeAlertIntent
    data class AreaText(val text: String) : HomeAlertIntent
    data class PickArea(val suggestion: LocationSuggestion) : HomeAlertIntent
    data class Radius(val km: Int) : HomeAlertIntent
    data class Budget(val max: Int?) : HomeAlertIntent
    data class ToggleType(val key: String) : HomeAlertIntent
    data object Save : HomeAlertIntent
    /** Switch on the card itself: off → saved straight away; on → saved if a spot is set, else the sheet opens. */
    data class QuickToggle(val on: Boolean) : HomeAlertIntent
    /** Active lease (not in notice) → ask before turning on. */
    data class LeaseContext(val needsConfirm: Boolean) : HomeAlertIntent
    data object ConfirmOn : HomeAlertIntent
    data object CancelConfirm : HomeAlertIntent
}

sealed interface HomeAlertEffect : UiEffect {
    data class Message(val text: String) : HomeAlertEffect
    data class Saved(val enabled: Boolean) : HomeAlertEffect
    data object NoLocation : HomeAlertEffect
}

private const val AREA_DEBOUNCE_MS = 300L

@HiltViewModel
class HomeAlertViewModel @Inject constructor(
    private val repository: com.saferenttechnologies.android.domain.repository.HomeAlertsRepository,
    private val location: LocationProvider,
    private val properties: PropertyRepository,
    private val browse: com.saferenttechnologies.android.domain.repository.BrowseRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<HomeAlertState, HomeAlertIntent, HomeAlertEffect>(HomeAlertState()) {

    private var areaJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { HomeAlertState() } } }
    }

    override suspend fun handleIntent(intent: HomeAlertIntent) {
        when (intent) {
            HomeAlertIntent.Load -> if (currentState.saved == null && !currentState.loading) load()
            HomeAlertIntent.Open -> {
                setState { copy(isOpen = true, draft = saved ?: HomeAlert(), showErrors = false) }
                if (currentState.saved == null) load()
                loadTypes()
            }
            HomeAlertIntent.Close -> if (!currentState.saving) setState { copy(isOpen = false) }
            is HomeAlertIntent.Enabled ->
                if (intent.on && currentState.needsConfirm && !currentState.draft.enabled) setState { copy(confirmOn = false) }
                else setState { copy(draft = draft.copy(enabled = intent.on)) }
            is HomeAlertIntent.LeaseContext -> setState { copy(needsConfirm = intent.needsConfirm) }
            HomeAlertIntent.CancelConfirm -> setState { copy(confirmOn = null) }
            HomeAlertIntent.ConfirmOn -> {
                val fromCard = currentState.confirmOn ?: return
                setState { copy(confirmOn = null) }
                if (fromCard) quickToggle(true) else setState { copy(draft = draft.copy(enabled = true)) }
            }
            HomeAlertIntent.UseMyLocation -> locate()
            HomeAlertIntent.LocationDenied -> sendEffect(HomeAlertEffect.NoLocation)
            HomeAlertIntent.OpenSearch -> setState { copy(searchOpen = true, searchText = "", suggestions = emptyList(), areaNotFound = false, areaSearching = false) }
            HomeAlertIntent.CloseSearch -> {
                areaJob?.cancel()
                setState { copy(searchOpen = false, areaSearching = false) }
            }
            is HomeAlertIntent.AreaText -> {
                val text = intent.text.take(80)
                setState { copy(searchText = text, areaNotFound = false, areaSearching = text.trim().length >= 2, suggestions = if (text.trim().length >= 2) suggestions else emptyList()) }
                areaJob?.cancel()
                if (text.trim().length >= 2) {
                    areaJob = viewModelScope.launch {
                        kotlinx.coroutines.delay(AREA_DEBOUNCE_MS)
                        // Same as Explore search: our own areas + cities (with listings).
                        val found = (browse.locations(text.trim()) as? AppResult.Success)?.data.orEmpty().filter { HomeAlertRules.searchable(it.kind) }
                        if (currentState.searchText != text) return@launch
                        setState { copy(areaSearching = false, suggestions = found, areaNotFound = found.isEmpty()) }
                    }
                }
            }
            is HomeAlertIntent.PickArea -> pickArea(intent.suggestion)
            is HomeAlertIntent.Radius -> setState { copy(draft = draft.copy(radiusKm = intent.km)) }
            is HomeAlertIntent.Budget -> setState { copy(draft = draft.copy(maxRent = intent.max)) }
            is HomeAlertIntent.ToggleType -> setState {
                val t = draft.propertyTypes
                copy(draft = draft.copy(propertyTypes = if (intent.key in t) t - intent.key else t + intent.key))
            }
            HomeAlertIntent.Save -> save()
            is HomeAlertIntent.QuickToggle ->
                if (intent.on && currentState.needsConfirm) setState { copy(confirmOn = true) } else quickToggle(intent.on)
        }
    }

    /** Card switch: off → save now; on → save if a place is set, else open the sheet. */
    private suspend fun quickToggle(on: Boolean) {
        val base = currentState.saved ?: HomeAlert()
        if (on && !base.hasWhere) {
            setState { copy(isOpen = true, draft = base.copy(enabled = true), showErrors = false) }
            loadTypes()
        } else {
            setState { copy(draft = base.copy(enabled = on)) }
            save()
        }
    }

    private suspend fun load() {
        setState { copy(loading = true) }
        when (val r = repository.get()) {
            is AppResult.Success -> setState {
                // Don't overwrite what the user already started changing in the sheet.
                copy(saved = r.data, draft = if (isOpen && draft != HomeAlert()) draft else r.data, loading = false)
            }
            is AppResult.Failure -> setState { copy(loading = false) }
        }
    }

    private suspend fun loadTypes() {
        if (currentState.types.isNotEmpty()) return
        (properties.propertyTypes() as? AppResult.Success)?.data?.let { t ->
            setState { copy(types = t.categories.flatMap { c -> c.types.filter { it.enabled }.map { AlertTypeOption(it.key, it.label) } }) }
        }
    }

    private suspend fun locate() {
        areaJob?.cancel()
        setState { copy(locating = true, areaSearching = false, areaNotFound = false, searchOpen = false) }
        val fix = location.currentFix()
        if (fix == null) {
            setState { copy(locating = false) }
            return sendEffect(HomeAlertEffect.NoLocation)
        }
        val address = location.addressOf(fix.latitude, fix.longitude)
        val label = HomeAlertRules.areaLabel(address)
        setState {
            copy(
                locating = false,
                suggestions = emptyList(),
                draft = draft.copy(latitude = fix.latitude, longitude = fix.longitude, areaLabel = label, locality = address?.locality.orEmpty(), city = address?.city.orEmpty()),
            )
        }
    }

    /** Area / city from our list → its name for matching + a centre point (server's, else the phone's Geocoder). */
    private suspend fun pickArea(s: LocationSuggestion) {
        areaJob?.cancel()
        val label = listOf(s.filter.locality, s.filter.city).filter { !it.isNullOrBlank() }.distinct().joinToString(", ").ifBlank { s.label }
        setState {
            copy(
                searchOpen = false,
                suggestions = emptyList(),
                areaSearching = s.latitude == null,
                areaNotFound = false,
                draft = draft.copy(areaLabel = label, locality = s.filter.locality.orEmpty(), city = s.filter.city.orEmpty(), latitude = s.latitude, longitude = s.longitude),
            )
        }
        if (s.latitude != null && s.longitude != null) return
        val fix = location.placeOf(label)
        if (currentState.draft.areaLabel != label) return
        setState { copy(areaSearching = false, draft = if (fix != null) draft.copy(latitude = fix.latitude, longitude = fix.longitude) else draft) }
    }

    private suspend fun save() {
        val d = currentState.draft
        if (!HomeAlertRules.canSave(d)) return setState { copy(showErrors = true) }
        setState { copy(saving = true) }
        when (val r = repository.save(d)) {
            is AppResult.Success -> {
                setState { copy(saving = false, saved = r.data, draft = r.data, isOpen = false) }
                sendEffect(HomeAlertEffect.Saved(r.data.enabled))
            }
            is AppResult.Failure -> {
                setState { copy(saving = false) }
                r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(HomeAlertEffect.Message(it)) }
            }
        }
    }
}
