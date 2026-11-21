package com.saferenttechnologies.android.presentation.safety

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.EmergencyContact
import com.saferenttechnologies.android.domain.model.Safety
import com.saferenttechnologies.android.domain.model.SafetyData
import com.saferenttechnologies.android.domain.model.SafetyRules
import com.saferenttechnologies.android.domain.repository.OwnerRepository
import com.saferenttechnologies.android.domain.repository.SafetyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyState(
    val isOpen: Boolean = false,
    val propertyId: String? = null,
    val title: String = "",
    /** Locality / city for "Find nearby" (Maps search, no paid API). */
    val near: String = "",
    val data: SafetyData? = null,
    val form: Safety = Safety(),
    val error: String? = null,
    val showErrors: Boolean = false,
    /** API issue paths → message (400). */
    val serverErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    /** docType being uploaded. */
    val uploading: String? = null,
) : UiState {
    val problems: Map<String, SafetyRules.Problem> get() = SafetyRules.validate(form)
    val changed: Boolean get() = data != null && SafetyRules.clean(data.safety) != SafetyRules.clean(form)
    /** Chip from the typed date (the server's state until edited). */
    val extinguisherState: String?
        get() = if (form.fireExtinguisher != true) null
        else if (form.fireExtinguisherExpiry == data?.safety?.fireExtinguisherExpiry && data?.extinguisherState != null) data.extinguisherState
        else SafetyRules.extinguisherState(form.fireExtinguisherExpiry)
}

sealed interface SafetyIntent : UiIntent {
    data class Open(val propertyId: String, val title: String = "", val near: String = "") : SafetyIntent
    data object Close : SafetyIntent
    data object Retry : SafetyIntent
    data class Edit(val safety: Safety) : SafetyIntent
    data object AddContact : SafetyIntent
    data class EditContact(val index: Int, val contact: EmergencyContact) : SafetyIntent
    data class RemoveContact(val index: Int) : SafetyIntent
    data object Save : SafetyIntent
    data class Upload(val docType: String, val uri: String) : SafetyIntent
}

sealed interface SafetyEffect : UiEffect {
    data class Message(val text: String) : SafetyEffect
}

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val repository: SafetyRepository,
    private val documents: OwnerRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<SafetyState, SafetyIntent, SafetyEffect>(SafetyState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { SafetyState() } } }
    }

    override suspend fun handleIntent(intent: SafetyIntent) {
        when (intent) {
            is SafetyIntent.Open -> {
                setState { SafetyState(isOpen = true, propertyId = intent.propertyId, title = intent.title, near = intent.near) }
                load()
            }
            SafetyIntent.Close -> if (!currentState.saving) setState { SafetyState() }
            SafetyIntent.Retry -> load()
            is SafetyIntent.Edit -> setState { copy(form = trimmed(intent.safety), serverErrors = emptyMap()) }
            SafetyIntent.AddContact -> if (currentState.form.emergencyContacts.size < SafetyRules.MAX_CONTACTS) {
                setState { copy(form = form.copy(emergencyContacts = form.emergencyContacts + EmergencyContact())) }
            }
            is SafetyIntent.EditContact -> setState {
                copy(
                    form = form.copy(emergencyContacts = form.emergencyContacts.mapIndexed { i, c -> if (i == intent.index) intent.contact.copy(label = intent.contact.label.take(60), phone = intent.contact.phone.take(16)) else c }),
                    serverErrors = emptyMap(),
                )
            }
            is SafetyIntent.RemoveContact -> setState { copy(form = form.copy(emergencyContacts = form.emergencyContacts.filterIndexed { i, _ -> i != intent.index }), serverErrors = emptyMap()) }
            SafetyIntent.Save -> save()
            is SafetyIntent.Upload -> upload(intent.docType, intent.uri)
        }
    }

    /** Length limits while typing. */
    private fun trimmed(s: Safety) = s.copy(
        smokeDetectorLocations = s.smokeDetectorLocations.take(SafetyRules.SMOKE_MAX),
        firstAidLocation = s.firstAidLocation.take(SafetyRules.FIRST_AID_MAX),
        securityName = s.securityName.take(SafetyRules.NAME_MAX),
        securityPhone = s.securityPhone.take(16),
        instructions = s.instructions.take(SafetyRules.INSTRUCTIONS_MAX),
        nearestHospital = s.nearestHospital.copy(name = s.nearestHospital.name.take(SafetyRules.NAME_MAX), phone = s.nearestHospital.phone.take(16), address = s.nearestHospital.address.take(300)),
        nearestPoliceStation = s.nearestPoliceStation.copy(name = s.nearestPoliceStation.name.take(SafetyRules.NAME_MAX), phone = s.nearestPoliceStation.phone.take(16), address = s.nearestPoliceStation.address.take(300)),
    )

    private suspend fun load() {
        val id = currentState.propertyId ?: return
        setState { copy(error = null) }
        when (val r = repository.safety(id)) {
            is AppResult.Success -> setState { copy(data = r.data, form = r.data.safety) }
            is AppResult.Failure -> {
                val msg = r.error.takeUnless { it is AppError.AccountBlocked }?.message
                setState { copy(error = msg ?: "") }
            }
        }
    }

    private suspend fun save() {
        val s = currentState
        val id = s.propertyId ?: return
        if (s.saving) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(saving = true) }
        val r = repository.saveSafety(id, s.form)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                val (message, data) = r.data
                setState { copy(data = data, form = data.safety, showErrors = false, serverErrors = emptyMap()) }
                sendEffect(SafetyEffect.Message(message.ifBlank { SAVED }))
            }
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.FieldIssues) setState { copy(serverErrors = e.issues, showErrors = true) }
                e.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(SafetyEffect.Message(it)) }
            }
        }
    }

    private suspend fun upload(docType: String, uri: String) {
        val id = currentState.propertyId ?: return
        if (currentState.uploading != null) return
        setState { copy(uploading = docType) }
        val r = documents.uploadPropertyDocument(id, uri, docType)
        setState { copy(uploading = null) }
        when (r) {
            is AppResult.Success -> {
                sendEffect(SafetyEffect.Message(r.data.ifBlank { UPLOADED }))
                // Fresh certificate rows (Uploaded / Verified), the form stays as typed.
                (repository.safety(id) as? AppResult.Success)?.let { ok -> setState { copy(data = data?.copy(certificates = ok.data.certificates, score = ok.data.score) ?: ok.data) } }
            }
            is AppResult.Failure -> r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(SafetyEffect.Message(it)) }
        }
    }

    companion object {
        const val SAVED = "@safety_saved"
        const val UPLOADED = "@safety_uploaded"
    }
}
