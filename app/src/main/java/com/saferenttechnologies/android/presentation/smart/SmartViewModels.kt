package com.saferenttechnologies.android.presentation.smart

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.InspectionComparison
import com.saferenttechnologies.android.domain.model.PropertyQr
import com.saferenttechnologies.android.domain.model.QrLinks
import com.saferenttechnologies.android.domain.model.QrResolution
import com.saferenttechnologies.android.domain.model.ReminderData
import com.saferenttechnologies.android.domain.model.ReminderRules
import com.saferenttechnologies.android.domain.model.ReminderSettings
import com.saferenttechnologies.android.domain.repository.SmartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun AppError.shown() = takeUnless { it is AppError.AccountBlocked }?.message

// ======================= move-in vs move-out =======================

data class ComparisonState(
    val isOpen: Boolean = false,
    val leaseId: String? = null,
    val title: String = "",
    val landlord: Boolean = false,
    val propertyId: String = "",
    val data: InspectionComparison? = null,
    val error: String? = null,
    val loading: Boolean = false,
    /** Photos: false = Move-in tab, true = Move-out tab. */
    val moveOutPhotos: Boolean = false,
    /** Same category side by side. */
    val compare: Boolean = false,
) : UiState

sealed interface ComparisonIntent : UiIntent {
    data class Open(val leaseId: String, val title: String = "", val landlord: Boolean = false, val propertyId: String = "") : ComparisonIntent
    data object Close : ComparisonIntent
    data object Refresh : ComparisonIntent
    data class PhotoTab(val moveOut: Boolean) : ComparisonIntent
    data class Compare(val on: Boolean) : ComparisonIntent
}

sealed interface ComparisonEffect : UiEffect

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    private val repository: SmartRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<ComparisonState, ComparisonIntent, ComparisonEffect>(ComparisonState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { ComparisonState() } } }
    }

    override suspend fun handleIntent(intent: ComparisonIntent) {
        when (intent) {
            is ComparisonIntent.Open -> {
                setState { ComparisonState(isOpen = true, leaseId = intent.leaseId, title = intent.title, landlord = intent.landlord, propertyId = intent.propertyId) }
                load()
            }
            ComparisonIntent.Close -> setState { ComparisonState() }
            ComparisonIntent.Refresh -> load()
            is ComparisonIntent.PhotoTab -> setState { copy(moveOutPhotos = intent.moveOut, compare = false) }
            is ComparisonIntent.Compare -> setState { copy(compare = intent.on) }
        }
    }

    private suspend fun load() {
        val id = currentState.leaseId ?: return
        if (currentState.loading) return
        setState { copy(loading = true, error = null) }
        when (val r = repository.comparison(id)) {
            is AppResult.Success -> setState { copy(data = r.data, loading = false) }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.shown().orEmpty()) }
        }
    }
}

// ======================= QR codes =======================

data class QrState(
    /** Owner "QR codes" screen. */
    val isOpen: Boolean = false,
    val propertyId: String? = null,
    val title: String = "",
    val codes: PropertyQr? = null,
    val error: String? = null,
    /** In-app scanner open. */
    val scanning: Boolean = false,
    val resolving: Boolean = false,
    /** Result sheet. */
    val resolution: QrResolution? = null,
) : UiState

sealed interface QrIntent : UiIntent {
    data class OpenCodes(val propertyId: String, val title: String = "") : QrIntent
    data object Close : QrIntent
    data object Retry : QrIntent
    data class Scan(val open: Boolean) : QrIntent
    /** Raw text from the camera (anything). */
    data class Scanned(val text: String) : QrIntent
    /** App link /p/{code}[/u/{unit}] (already parsed). */
    data class Resolve(val code: String, val unit: String?) : QrIntent
    data object DismissResult : QrIntent
}

sealed interface QrEffect : UiEffect {
    data class Message(val text: String) : QrEffect
}

@HiltViewModel
class QrViewModel @Inject constructor(
    private val repository: SmartRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<QrState, QrIntent, QrEffect>(QrState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { QrState() } } }
    }

    override suspend fun handleIntent(intent: QrIntent) {
        when (intent) {
            is QrIntent.OpenCodes -> {
                setState { copy(isOpen = true, propertyId = intent.propertyId, title = intent.title, codes = null, error = null) }
                loadCodes()
            }
            QrIntent.Close -> setState { copy(isOpen = false, propertyId = null, codes = null, error = null) }
            QrIntent.Retry -> loadCodes()
            is QrIntent.Scan -> setState { copy(scanning = intent.open) }
            is QrIntent.Scanned -> {
                val code = QrLinks.parse(intent.text)
                if (code == null) {
                    sendEffect(QrEffect.Message(NOT_SAFERENT))
                } else {
                    setState { copy(scanning = false) }
                    resolve(code.property, code.unit)
                }
            }
            is QrIntent.Resolve -> resolve(intent.code, intent.unit)
            QrIntent.DismissResult -> setState { copy(resolution = null) }
        }
    }

    private suspend fun loadCodes() {
        val id = currentState.propertyId ?: return
        setState { copy(error = null) }
        when (val r = repository.propertyQr(id)) {
            is AppResult.Success -> setState { copy(codes = r.data) }
            is AppResult.Failure -> setState { copy(error = r.error.shown().orEmpty()) }
        }
    }

    private suspend fun resolve(code: String, unit: String?) {
        if (currentState.resolving) return
        setState { copy(resolving = true) }
        val r = repository.resolveQr(code, unit)
        setState { copy(resolving = false) }
        when (r) {
            is AppResult.Success -> setState { copy(resolution = r.data) }
            // 404 "This QR code isn't a SafeRent property." / 429 too many scans.
            is AppResult.Failure -> r.error.shown()?.let { sendEffect(QrEffect.Message(it)) }
        }
    }

    companion object {
        const val NOT_SAFERENT = "@qr_not_saferent"
    }
}

// ======================= smart rent reminders =======================

data class ReminderState(
    val isOpen: Boolean = false,
    val data: ReminderData? = null,
    val form: ReminderSettings = ReminderSettings(),
    val error: String? = null,
    val saving: Boolean = false,
) : UiState {
    val changed: Boolean get() = data != null && ReminderRules.clean(data.settings) != ReminderRules.clean(form)
}

sealed interface ReminderIntent : UiIntent {
    data object Open : ReminderIntent
    data object Close : ReminderIntent
    data object Retry : ReminderIntent
    data class Enabled(val on: Boolean) : ReminderIntent
    data class SmartEarly(val on: Boolean) : ReminderIntent
    data class ToggleBefore(val day: Int) : ReminderIntent
    data class ToggleOverdue(val day: Int) : ReminderIntent
    data object Defaults : ReminderIntent
    data object Save : ReminderIntent
}

sealed interface ReminderEffect : UiEffect {
    data class Message(val text: String) : ReminderEffect
}

/** Landlord Rent → "Reminders". */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repository: SmartRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<ReminderState, ReminderIntent, ReminderEffect>(ReminderState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { ReminderState() } } }
    }

    override suspend fun handleIntent(intent: ReminderIntent) {
        when (intent) {
            ReminderIntent.Open -> {
                setState { ReminderState(isOpen = true) }
                load()
            }
            ReminderIntent.Close -> if (!currentState.saving) setState { ReminderState() }
            ReminderIntent.Retry -> load()
            is ReminderIntent.Enabled -> setState { copy(form = form.copy(enabled = intent.on)) }
            is ReminderIntent.SmartEarly -> setState { copy(form = form.copy(smartEarly = intent.on)) }
            is ReminderIntent.ToggleBefore -> setState { copy(form = form.copy(beforeDays = ReminderRules.toggleBefore(form.beforeDays, intent.day))) }
            is ReminderIntent.ToggleOverdue -> setState { copy(form = form.copy(overdueDays = ReminderRules.toggleOverdue(form.overdueDays, intent.day))) }
            ReminderIntent.Defaults -> currentState.data?.let { d -> setState { copy(form = d.defaults) } }
            ReminderIntent.Save -> save()
        }
    }

    private suspend fun load() {
        setState { copy(error = null) }
        when (val r = repository.reminders()) {
            is AppResult.Success -> setState { copy(data = r.data, form = r.data.settings) }
            is AppResult.Failure -> setState { copy(error = r.error.shown().orEmpty()) }
        }
    }

    private suspend fun save() {
        if (currentState.saving) return
        setState { copy(saving = true) }
        val r = repository.saveReminders(currentState.form)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(data = r.data.second, form = r.data.second.settings) }
                sendEffect(ReminderEffect.Message(r.data.first))
            }
            is AppResult.Failure -> r.error.shown()?.let { sendEffect(ReminderEffect.Message(it)) }
        }
    }
}
