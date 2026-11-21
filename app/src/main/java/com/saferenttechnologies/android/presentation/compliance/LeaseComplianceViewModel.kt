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
import com.saferenttechnologies.android.domain.model.ComplianceRules
import com.saferenttechnologies.android.domain.model.LeaseCompliance
import com.saferenttechnologies.android.domain.repository.ComplianceRepository
import com.saferenttechnologies.android.domain.repository.OwnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaseComplianceState(
    val leaseId: String? = null,
    val propertyId: String = "",
    val data: LeaseCompliance? = null,
    val form: LeaseCompliance = LeaseCompliance(),
    val error: String? = null,
    val showErrors: Boolean = false,
    val serverErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val uploading: String? = null,
) : UiState {
    val problems: Map<String, ComplianceRules.Problem> get() = ComplianceRules.validateLease(form)
    val changes: Map<String, Any?> get() = data?.let { ComplianceRules.leaseChanges(it, form) }.orEmpty()
}

sealed interface LeaseComplianceIntent : UiIntent {
    data class Load(val leaseId: String, val propertyId: String = "") : LeaseComplianceIntent
    data object Clear : LeaseComplianceIntent
    data class Edit(val form: LeaseCompliance) : LeaseComplianceIntent
    data object Save : LeaseComplianceIntent
    /** Landlord: RENTAL_AGREEMENT / POLICE_VERIFICATION to the property's documents. */
    data class Upload(val docType: String, val uri: String) : LeaseComplianceIntent
}

sealed interface LeaseComplianceEffect : UiEffect {
    data class Message(val text: String) : LeaseComplianceEffect
}

/** Lease detail cards: rental agreement + police verification (landlord and tenant). */
@HiltViewModel
class LeaseComplianceViewModel @Inject constructor(
    private val repository: ComplianceRepository,
    private val documents: OwnerRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<LeaseComplianceState, LeaseComplianceIntent, LeaseComplianceEffect>(LeaseComplianceState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { LeaseComplianceState() } } }
    }

    override suspend fun handleIntent(intent: LeaseComplianceIntent) {
        when (intent) {
            is LeaseComplianceIntent.Load -> {
                val same = currentState.leaseId == intent.leaseId
                setState { if (same) copy(propertyId = intent.propertyId.ifBlank { propertyId }) else LeaseComplianceState(leaseId = intent.leaseId, propertyId = intent.propertyId) }
                if (!same || currentState.data == null) load()
            }
            LeaseComplianceIntent.Clear -> setState { LeaseComplianceState() }
            is LeaseComplianceIntent.Edit -> setState {
                val f = intent.form
                copy(
                    form = f.copy(
                        agreement = f.agreement.copy(
                            registrationNumber = f.agreement.registrationNumber.take(ComplianceRules.AGREEMENT_NUMBER_MAX),
                            office = f.agreement.office.take(ComplianceRules.OFFICE_MAX),
                            stampDutyAmount = ComplianceRules.amountInput(f.agreement.stampDutyAmount),
                        ),
                        police = f.police.copy(reference = f.police.reference.take(ComplianceRules.REFERENCE_MAX)),
                    ),
                    serverErrors = emptyMap(),
                )
            }
            LeaseComplianceIntent.Save -> save()
            is LeaseComplianceIntent.Upload -> upload(intent.docType, intent.uri)
        }
    }

    private suspend fun load() {
        val id = currentState.leaseId ?: return
        setState { copy(error = null) }
        when (val r = repository.leaseCompliance(id)) {
            is AppResult.Success -> if (currentState.leaseId == id) setState { copy(data = r.data, form = r.data) }
            is AppResult.Failure -> if (currentState.leaseId == id) setState { copy(error = r.error.takeUnless { it is AppError.AccountBlocked }?.message ?: "") }
        }
    }

    private suspend fun save() {
        val s = currentState
        val id = s.leaseId ?: return
        if (s.saving) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        val changes = s.changes
        if (changes.isEmpty()) return
        setState { copy(saving = true) }
        val r = repository.saveLeaseCompliance(id, changes)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(data = r.data.second, form = r.data.second, showErrors = false, serverErrors = emptyMap()) }
                sendEffect(LeaseComplianceEffect.Message(r.data.first))
            }
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.FieldIssues) setState { copy(serverErrors = e.issues, showErrors = true) }
                // 403 (agreement is the landlord's) / 412 LEASE_ENDED → fresh "can".
                if (e is AppError.InvalidState || (e is AppError.Server && e.httpCode in setOf(403, 412))) load()
                e.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(LeaseComplianceEffect.Message(it)) }
            }
        }
    }

    private suspend fun upload(docType: String, uri: String) {
        val pid = currentState.propertyId.ifBlank { return }
        if (currentState.uploading != null) return
        setState { copy(uploading = docType) }
        val r = documents.uploadPropertyDocument(pid, uri, docType)
        setState { copy(uploading = null) }
        when (r) {
            is AppResult.Success -> sendEffect(LeaseComplianceEffect.Message(r.data))
            is AppResult.Failure -> r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(LeaseComplianceEffect.Message(it)) }
        }
    }
}
