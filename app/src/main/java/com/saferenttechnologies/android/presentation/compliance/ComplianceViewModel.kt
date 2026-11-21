package com.saferenttechnologies.android.presentation.compliance

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ComplianceData
import com.saferenttechnologies.android.domain.model.ComplianceRules
import com.saferenttechnologies.android.domain.model.PropertyCompliance
import com.saferenttechnologies.android.domain.repository.ComplianceRepository
import com.saferenttechnologies.android.domain.repository.OwnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComplianceState(
    val isOpen: Boolean = false,
    val propertyId: String? = null,
    val title: String = "",
    val data: ComplianceData? = null,
    val form: PropertyCompliance = PropertyCompliance(),
    val error: String? = null,
    val showErrors: Boolean = false,
    val serverErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    /** Declaration: "I agree" ticked. */
    val agree: Boolean = false,
    val declaring: Boolean = false,
    /** 403 "Only the owner …" → managers see "Waiting for the owner". */
    val ownerOnly: Boolean = false,
    val uploading: String? = null,
) : UiState {
    val problems: Map<String, ComplianceRules.Problem> get() = ComplianceRules.validate(form)
    val changed: Boolean get() = data != null && ComplianceRules.clean(data.compliance) != ComplianceRules.clean(form)
}

sealed interface ComplianceIntent : UiIntent {
    data class Open(val propertyId: String, val title: String = "") : ComplianceIntent
    data object Close : ComplianceIntent
    data object Retry : ComplianceIntent
    data class Edit(val form: PropertyCompliance) : ComplianceIntent
    data class Agree(val agree: Boolean) : ComplianceIntent
    data object Declare : ComplianceIntent
    data object Save : ComplianceIntent
    data class Upload(val docType: String, val uri: String, val expiryDate: String?) : ComplianceIntent
}

sealed interface ComplianceEffect : UiEffect {
    data class Message(val text: String) : ComplianceEffect
}

@HiltViewModel
class ComplianceViewModel @Inject constructor(
    private val repository: ComplianceRepository,
    private val documents: OwnerRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<ComplianceState, ComplianceIntent, ComplianceEffect>(ComplianceState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { ComplianceState() } } }
    }

    override suspend fun handleIntent(intent: ComplianceIntent) {
        when (intent) {
            is ComplianceIntent.Open -> {
                setState { ComplianceState(isOpen = true, propertyId = intent.propertyId, title = intent.title) }
                load()
            }
            ComplianceIntent.Close -> if (!currentState.saving && !currentState.declaring) setState { ComplianceState() }
            ComplianceIntent.Retry -> load()
            is ComplianceIntent.Edit -> setState {
                val f = intent.form
                copy(
                    form = f.copy(
                        assessmentNumber = f.assessmentNumber.take(ComplianceRules.ASSESSMENT_MAX),
                        propertyTaxAmount = ComplianceRules.amountInput(f.propertyTaxAmount),
                        buildingApprovalNumber = f.buildingApprovalNumber.take(ComplianceRules.APPROVAL_MAX),
                        buildingApprovalAuthority = f.buildingApprovalAuthority.take(ComplianceRules.AUTHORITY_MAX),
                    ),
                    serverErrors = emptyMap(),
                )
            }
            is ComplianceIntent.Agree -> setState { copy(agree = intent.agree) }
            ComplianceIntent.Declare -> declare()
            ComplianceIntent.Save -> save()
            is ComplianceIntent.Upload -> upload(intent.docType, intent.uri, intent.expiryDate)
        }
    }

    private suspend fun load() {
        val id = currentState.propertyId ?: return
        setState { copy(error = null) }
        when (val r = repository.compliance(id)) {
            is AppResult.Success -> setState { copy(data = r.data, form = r.data.compliance) }
            is AppResult.Failure -> setState { copy(error = r.error.takeUnless { it is AppError.AccountBlocked }?.message ?: "") }
        }
    }

    private suspend fun declare() {
        val s = currentState
        val id = s.propertyId ?: return
        if (!s.agree || s.declaring) return
        setState { copy(declaring = true) }
        val r = repository.declare(id, s.data?.declaration?.version ?: ComplianceRules.DECLARATION_VERSION)
        setState { copy(declaring = false) }
        when (r) {
            is AppResult.Success -> {
                // Only the declaration changed: keep the typed form.
                setState { copy(data = data?.copy(declaration = r.data.second.declaration) ?: r.data.second, agree = false) }
                sendEffect(ComplianceEffect.Message(r.data.first))
            }
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.Server && e.httpCode == 403) setState { copy(ownerOnly = true, agree = false) }
                // "The declaration text changed" → fresh text to read again.
                if (e is AppError.FieldIssues || (e is AppError.Server && e.httpCode == 409)) load()
                e.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(ComplianceEffect.Message(it)) }
            }
        }
    }

    private suspend fun save() {
        val s = currentState
        val id = s.propertyId ?: return
        if (s.saving) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(saving = true) }
        val r = repository.saveCompliance(id, s.form)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(data = r.data.second, form = r.data.second.compliance, showErrors = false, serverErrors = emptyMap()) }
                sendEffect(ComplianceEffect.Message(r.data.first))
            }
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.FieldIssues) setState { copy(serverErrors = e.issues, showErrors = true) }
                e.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(ComplianceEffect.Message(it)) }
            }
        }
    }

    private suspend fun upload(docType: String, uri: String, expiryDate: String?) {
        val id = currentState.propertyId ?: return
        if (currentState.uploading != null) return
        setState { copy(uploading = docType) }
        val r = documents.uploadPropertyDocument(id, uri, docType, expiryDate)
        setState { copy(uploading = null) }
        when (r) {
            is AppResult.Success -> {
                sendEffect(ComplianceEffect.Message(r.data))
                // Fresh checklist / expiry chips; the typed form stays.
                (repository.compliance(id) as? AppResult.Success)?.let { ok -> setState { copy(data = ok.data.copy(compliance = data?.compliance ?: ok.data.compliance)) } }
            }
            is AppResult.Failure -> r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(ComplianceEffect.Message(it)) }
        }
    }
}
