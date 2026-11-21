package com.saferenttechnologies.android.presentation.owner

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.OwnerDetails
import com.saferenttechnologies.android.domain.model.OwnerRules
import com.saferenttechnologies.android.domain.repository.OwnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerDetailsState(
    val isOpen: Boolean = false,
    val loading: Boolean = false,
    val loadError: String? = null,
    /** As saved on the server (null = not loaded). */
    val original: OwnerDetails? = null,
    val form: OwnerDetails = OwnerDetails(),
    val saving: Boolean = false,
    /** Save tapped with problems → show the local checks. */
    val showErrors: Boolean = false,
    /** 400 details.issues: field → message. */
    val serverErrors: Map<String, String> = emptyMap(),
    /** 403: not a landlord → no screen, no home card. */
    val forbidden: Boolean = false,
    /** Getting the address from the phone's location. */
    val locating: Boolean = false,
    /** Automatic fill already tried on this open (don't ask again). */
    val autoLocated: Boolean = false,
) : UiState {
    val changes: Map<String, String?> get() = original?.let { OwnerRules.changes(it, form) }.orEmpty()
    val problems: Map<String, OwnerRules.Problem> get() = OwnerRules.validate(form)
    val canSave: Boolean get() = !saving && original != null && changes.isNotEmpty()
    /** Landlord home "Complete owner details" card. */
    val showHomeCard: Boolean get() = !forbidden && original?.incomplete == true
}

sealed interface OwnerDetailsIntent : UiIntent {
    /** Landlord home: load quietly for the "Complete owner details" card. */
    data object LoadSummary : OwnerDetailsIntent
    data object Open : OwnerDetailsIntent
    data object Close : OwnerDetailsIntent
    data object Retry : OwnerDetailsIntent
    /** [field] = API name (alternatePhone, city, preferredLanguage …). */
    data class Edit(val field: String, val value: String) : OwnerDetailsIntent
    data object Save : OwnerDetailsIntent
    /** Fill the address from the current location ([auto] = on open: only empty fields, quiet). */
    data class UseLocation(val auto: Boolean = false) : OwnerDetailsIntent
    /** Automatic fill skipped (permission refused). */
    data object SkipAutoLocation : OwnerDetailsIntent
}

sealed interface OwnerDetailsEffect : UiEffect {
    data class Message(val text: String) : OwnerDetailsEffect
}

/** Profile → "Owner details": GET / PUT landlord/owner-details (only changed fields). Private, never logged. */
@HiltViewModel
class OwnerDetailsViewModel @Inject constructor(
    private val repository: OwnerRepository,
    private val reverseGeocode: com.saferenttechnologies.android.domain.usecase.ReverseGeocodeUseCase,
    sessionEvents: SessionEvents,
) : MviViewModel<OwnerDetailsState, OwnerDetailsIntent, OwnerDetailsEffect>(OwnerDetailsState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { OwnerDetailsState() } } }
    }

    override suspend fun handleIntent(intent: OwnerDetailsIntent) {
        when (intent) {
            OwnerDetailsIntent.LoadSummary -> if (!currentState.loading && !currentState.isOpen) load()
            OwnerDetailsIntent.Open -> if (!currentState.forbidden) {
                setState { copy(isOpen = true, showErrors = false, serverErrors = emptyMap(), form = original ?: form, autoLocated = false) }
                load()
            }
            OwnerDetailsIntent.Close -> if (!currentState.saving) setState { copy(isOpen = false, form = original ?: OwnerDetails(), showErrors = false, serverErrors = emptyMap()) }
            OwnerDetailsIntent.Retry -> load()
            is OwnerDetailsIntent.Edit -> if (!currentState.saving) setState {
                copy(form = form.with(intent.field, intent.value), serverErrors = serverErrors - intent.field)
            }
            OwnerDetailsIntent.Save -> save()
            is OwnerDetailsIntent.UseLocation -> useLocation(intent.auto)
            OwnerDetailsIntent.SkipAutoLocation -> setState { copy(autoLocated = true) }
        }
    }

    /** One GPS fix → device Geocoder → address fields (nothing about it is logged). */
    private suspend fun useLocation(auto: Boolean) {
        val s = currentState
        if (s.locating || s.saving || s.original == null) return
        if (auto && (s.autoLocated || !OwnerRules.addressEmpty(s.form))) return
        setState { copy(locating = true, autoLocated = true) }
        val r = reverseGeocode()
        setState { copy(locating = false) }
        when (r) {
            is com.saferenttechnologies.android.domain.usecase.LocationLookup.Found -> {
                val geo = r.address
                if (geo == null) {
                    sendEffect(OwnerDetailsEffect.Message(NO_ADDRESS))
                } else {
                    setState {
                        val next = OwnerRules.withLocation(form, geo, onlyEmpty = auto)
                        copy(form = next, serverErrors = serverErrors - setOf("address", "city", "state", "pincode"))
                    }
                    sendEffect(OwnerDetailsEffect.Message(FILLED))
                }
            }
            com.saferenttechnologies.android.domain.usecase.LocationLookup.NoFix -> if (!auto) sendEffect(OwnerDetailsEffect.Message(NO_FIX))
        }
    }

    private suspend fun load() {
        setState { copy(loading = original == null, loadError = null) }
        when (val r = repository.ownerDetails()) {
            is AppResult.Success -> setState {
                // Keep what's being typed on the open screen.
                val typing = isOpen && original != null && changes.isNotEmpty()
                copy(loading = false, original = r.data, form = if (typing) form else r.data)
            }
            is AppResult.Failure -> {
                val e = r.error
                val forbidden = e is AppError.Server && e.httpCode == 403
                setState { copy(loading = false, forbidden = forbidden, isOpen = isOpen && !forbidden, loadError = if (e is AppError.AccountBlocked || forbidden) null else e.message) }
            }
        }
    }

    private suspend fun save() {
        val s = currentState
        if (!s.canSave) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(saving = true, serverErrors = emptyMap()) }
        when (val r = repository.saveOwnerDetails(s.changes)) {
            is AppResult.Success -> {
                val saved = r.data.second ?: s.form
                setState { copy(saving = false, original = saved, form = saved, showErrors = false) }
                sendEffect(OwnerDetailsEffect.Message(r.data.first.ifBlank { SAVED }))
            }
            is AppResult.Failure -> {
                setState { copy(saving = false) }
                when (val e = r.error) {
                    // Message under the matching field; nothing matches → snackbar.
                    is AppError.FieldIssues -> {
                        val known = e.issues.filterKeys { it in s.form.editable().keys }
                        setState { copy(serverErrors = known, showErrors = true) }
                        if (known.isEmpty()) sendEffect(OwnerDetailsEffect.Message(e.issues.values.firstOrNull() ?: e.message))
                    }
                    is AppError.AccountBlocked -> Unit
                    else -> sendEffect(OwnerDetailsEffect.Message(e.message))
                }
            }
        }
    }

    private fun OwnerDetails.with(field: String, v: String): OwnerDetails = when (field) {
        "alternatePhone" -> copy(alternatePhone = v.take(20))
        "address" -> copy(address = v.take(255))
        "city" -> copy(city = v.take(80))
        "state" -> copy(state = v.take(80))
        "pincode" -> copy(pincode = v.filter(Char::isDigit).take(6))
        "preferredLanguage" -> copy(preferredLanguage = v)
        "preferredContactMethod" -> copy(preferredContactMethod = v)
        "emergencyContactName" -> copy(emergencyContactName = v.take(120))
        "emergencyContactPhone" -> copy(emergencyContactPhone = v.take(20))
        "emergencyContactRelation" -> copy(emergencyContactRelation = v.take(40))
        else -> this
    }

    companion object {
        const val SAVED = "Saved"
        const val FILLED = "@owner_location_filled"
        const val NO_ADDRESS = "@owner_location_no_address"
        const val NO_FIX = "@owner_location_no_fix"
    }
}
