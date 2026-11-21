package com.saferenttechnologies.android.presentation.visits

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Visit
import com.saferenttechnologies.android.domain.model.VisitRules
import com.saferenttechnologies.android.domain.model.VisitSettings
import com.saferenttechnologies.android.domain.model.VisitSettingsData
import com.saferenttechnologies.android.domain.model.WeeklyWindow
import com.saferenttechnologies.android.domain.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A host action that needs a typed reason / note: reject, cancel (reason 3–300) or complete (optional outcome). */
data class VisitAsk(val visitId: String, val action: String, val text: String = "")

data class HostVisitsState(
    val isOpen: Boolean = false,
    /** null = all my properties (no Availability tab). */
    val propertyId: String? = null,
    val title: String = "",
    val availabilityTab: Boolean = false,
    val items: List<Visit>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val focusId: String? = null,
    val ask: VisitAsk? = null,
    val busy: Boolean = false,
    // availability
    val settingsData: VisitSettingsData? = null,
    val form: VisitSettings = VisitSettings(),
    val showErrors: Boolean = false,
    val serverErrors: Map<String, String> = emptyMap(),
    val savingSettings: Boolean = false,
) : UiState {
    val problems: Map<String, VisitRules.Problem> get() = VisitRules.validate(form)
    val settingsChanged: Boolean get() = settingsData != null && settingsData.settings != form
}

sealed interface HostVisitsIntent : UiIntent {
    data class Open(val propertyId: String?, val title: String = "", val focusId: String? = null, val availability: Boolean = false) : HostVisitsIntent
    data object Close : HostVisitsIntent
    data class Tab(val availability: Boolean) : HostVisitsIntent
    data object Refresh : HostVisitsIntent
    data class Confirm(val visitId: String) : HostVisitsIntent
    data class NoShow(val visitId: String) : HostVisitsIntent
    data class Ask(val ask: VisitAsk?) : HostVisitsIntent
    data object SendAsk : HostVisitsIntent
    // availability
    data class EditSettings(val settings: VisitSettings) : HostVisitsIntent
    data class AddWindow(val window: WeeklyWindow) : HostVisitsIntent
    data class EditWindow(val index: Int, val window: WeeklyWindow) : HostVisitsIntent
    data class RemoveWindow(val index: Int) : HostVisitsIntent
    data class Preset(val windows: List<WeeklyWindow>) : HostVisitsIntent
    data class BlockDate(val date: String) : HostVisitsIntent
    data class UnblockDate(val date: String) : HostVisitsIntent
    data object SaveSettings : HostVisitsIntent
}

sealed interface HostVisitsEffect : UiEffect {
    data class Message(val text: String) : HostVisitsEffect
}

/** Owner / manager / host: visit requests + a property's availability. Never logs phones or notes. */
@HiltViewModel
class HostVisitsViewModel @Inject constructor(
    private val repository: VisitRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<HostVisitsState, HostVisitsIntent, HostVisitsEffect>(HostVisitsState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { HostVisitsState() } } }
    }

    override suspend fun handleIntent(intent: HostVisitsIntent) {
        when (intent) {
            is HostVisitsIntent.Open -> {
                val same = currentState.propertyId == intent.propertyId
                setState {
                    HostVisitsState(
                        isOpen = true, propertyId = intent.propertyId, title = intent.title, focusId = intent.focusId,
                        availabilityTab = intent.availability && intent.propertyId != null,
                        items = if (same) items else null, settingsData = if (same) settingsData else null, form = if (same) form else VisitSettings(),
                    )
                }
                load()
                intent.propertyId?.let { loadSettings(it) }
            }
            HostVisitsIntent.Close -> if (!currentState.busy && !currentState.savingSettings) setState { copy(isOpen = false, ask = null) }
            is HostVisitsIntent.Tab -> setState { copy(availabilityTab = intent.availability && propertyId != null) }
            HostVisitsIntent.Refresh -> load()
            is HostVisitsIntent.Confirm -> act(intent.visitId, "confirm")
            is HostVisitsIntent.NoShow -> act(intent.visitId, "no-show")
            is HostVisitsIntent.Ask -> setState { copy(ask = intent.ask?.let { it.copy(text = it.text.take(300)) }) }
            HostVisitsIntent.SendAsk -> currentState.ask?.let { a ->
                when (a.action) {
                    "complete" -> act(a.visitId, "complete", outcome = a.text)
                    else -> if (VisitRules.reasonOk(a.text)) act(a.visitId, a.action, reason = a.text)
                }
            }

            is HostVisitsIntent.EditSettings -> edit { intent.settings.copy(instructions = intent.settings.instructions.take(300)) }
            is HostVisitsIntent.AddWindow -> edit { it.copy(weekly = (it.weekly + intent.window).take(VisitRules.MAX_WINDOWS)) }
            is HostVisitsIntent.EditWindow -> edit { it.copy(weekly = it.weekly.mapIndexed { i, w -> if (i == intent.index) intent.window else w }) }
            is HostVisitsIntent.RemoveWindow -> edit { it.copy(weekly = it.weekly.filterIndexed { i, _ -> i != intent.index }) }
            is HostVisitsIntent.Preset -> edit { it.copy(weekly = VisitRules.addPreset(it.weekly, intent.windows)) }
            is HostVisitsIntent.BlockDate -> edit { it.copy(blockedDates = (it.blockedDates + intent.date).distinct().sorted().take(VisitRules.MAX_BLOCKED)) }
            is HostVisitsIntent.UnblockDate -> edit { it.copy(blockedDates = it.blockedDates - intent.date) }
            HostVisitsIntent.SaveSettings -> saveSettings()
        }
    }

    private fun edit(change: (VisitSettings) -> VisitSettings) {
        if (currentState.savingSettings) return
        setState { copy(form = change(form), serverErrors = emptyMap()) }
    }

    private suspend fun load() {
        setState { copy(loading = true, error = null) }
        when (val r = repository.hostVisits(currentState.propertyId)) {
            is AppResult.Success -> setState { copy(loading = false, items = r.data) }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun loadSettings(id: String) {
        when (val r = repository.settings(id)) {
            is AppResult.Success -> if (currentState.propertyId == id) setState {
                // Keep what's being edited.
                copy(settingsData = r.data, form = if (settingsChanged) form else r.data.settings)
            }
            is AppResult.Failure -> if (r.error !is AppError.AccountBlocked) sendEffect(HostVisitsEffect.Message(r.error.message))
        }
    }

    private suspend fun act(visitId: String, action: String, reason: String? = null, outcome: String? = null) {
        if (currentState.busy) return
        setState { copy(busy = true) }
        val r = repository.hostAction(visitId, action, reason, outcome)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(ask = null) }
                sendEffect(HostVisitsEffect.Message(r.data.first))
                r.data.second?.let { v -> setState { copy(items = items?.map { if (it.id == v.id) v else it }) } }
                load()
            }
            is AppResult.Failure -> {
                if (r.error !is AppError.AccountBlocked) sendEffect(HostVisitsEffect.Message(r.error.message))
                load()
            }
        }
    }

    private suspend fun saveSettings() {
        val s = currentState
        val id = s.propertyId ?: return
        if (s.savingSettings) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(savingSettings = true, serverErrors = emptyMap()) }
        val r = repository.saveSettings(id, s.form)
        setState { copy(savingSettings = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(settingsData = r.data.second, form = r.data.second.settings, showErrors = false) }
                sendEffect(HostVisitsEffect.Message(r.data.first.ifBlank { SAVED }))
            }
            is AppResult.Failure -> when (val e = r.error) {
                is AppError.FieldIssues -> {
                    setState { copy(serverErrors = e.issues, showErrors = true) }
                    sendEffect(HostVisitsEffect.Message(e.issues.values.firstOrNull() ?: e.message))
                }
                is AppError.AccountBlocked -> Unit
                else -> sendEffect(HostVisitsEffect.Message(e.message))
            }
        }
    }

    companion object {
        const val SAVED = "Visit settings saved"
    }
}
