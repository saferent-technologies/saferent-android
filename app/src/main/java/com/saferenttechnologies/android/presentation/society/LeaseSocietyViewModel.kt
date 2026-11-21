package com.saferenttechnologies.android.presentation.society

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.LeaseSociety
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.SocietyApproval
import com.saferenttechnologies.android.domain.model.SocietyRules
import com.saferenttechnologies.android.domain.repository.SocietyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaseSocietyState(
    val leaseId: String? = null,
    val data: LeaseSociety? = null,
    /** 412 LEASE_ENDED / not a society → no card. */
    val gone: Boolean = false,
    val error: String? = null,
    /** Landlord "Update" sheet. */
    val approval: SocietyApproval? = null,
    val showErrors: Boolean = false,
    val serverErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
) : UiState {
    val problems: Map<String, SocietyRules.Problem> get() = approval?.let { SocietyRules.validateApproval(it) }.orEmpty()
}

sealed interface LeaseSocietyIntent : UiIntent {
    data class Load(val leaseId: String) : LeaseSocietyIntent
    data object Clear : LeaseSocietyIntent
    data object OpenApproval : LeaseSocietyIntent
    data class EditApproval(val approval: SocietyApproval) : LeaseSocietyIntent
    data object CloseApproval : LeaseSocietyIntent
    data object SaveApproval : LeaseSocietyIntent
}

sealed interface LeaseSocietyEffect : UiEffect {
    data class Message(val text: String) : LeaseSocietyEffect
}

/** Lease detail "Society" card: contacts, my parking, rules, society approval. */
@HiltViewModel
class LeaseSocietyViewModel @Inject constructor(
    private val repository: SocietyRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<LeaseSocietyState, LeaseSocietyIntent, LeaseSocietyEffect>(LeaseSocietyState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { LeaseSocietyState() } } }
    }

    override suspend fun handleIntent(intent: LeaseSocietyIntent) {
        when (intent) {
            is LeaseSocietyIntent.Load -> {
                if (currentState.leaseId != intent.leaseId) setState { LeaseSocietyState(leaseId = intent.leaseId) }
                load()
            }
            LeaseSocietyIntent.Clear -> setState { LeaseSocietyState() }
            LeaseSocietyIntent.OpenApproval -> currentState.data?.let { d ->
                val a = d.approval
                setState { copy(approval = a.copy(status = a.status ?: "PENDING"), showErrors = false, serverErrors = emptyMap()) }
            }
            is LeaseSocietyIntent.EditApproval -> setState {
                val a = intent.approval
                // Approved / rejected → today as the date (changeable).
                val date = if ((a.status == "APPROVED" || a.status == "REJECTED") && a.date == null) SimpleDate.today().iso() else a.date
                copy(approval = a.copy(date = date, note = a.note.take(500)), serverErrors = emptyMap())
            }
            LeaseSocietyIntent.CloseApproval -> if (!currentState.saving) setState { copy(approval = null) }
            LeaseSocietyIntent.SaveApproval -> save()
        }
    }

    private suspend fun load() {
        val id = currentState.leaseId ?: return
        setState { copy(error = null) }
        when (val r = repository.leaseSociety(id)) {
            is AppResult.Success -> if (currentState.leaseId == id) setState { copy(data = r.data, gone = false) }
            is AppResult.Failure -> if (currentState.leaseId == id) {
                val e = r.error
                if (e is AppError.InvalidState || (e is AppError.Server && e.httpCode in setOf(404, 412))) setState { copy(gone = true, data = null) }
                else setState { copy(error = e.takeUnless { it is AppError.AccountBlocked }?.message ?: "") }
            }
        }
    }

    private suspend fun save() {
        val s = currentState
        val id = s.leaseId ?: return
        val a = s.approval ?: return
        if (s.saving) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(saving = true) }
        val r = repository.saveApproval(id, a)
        setState { copy(saving = false) }
        when (r) {
            is AppResult.Success -> {
                setState { copy(data = r.data.second, approval = null) }
                sendEffect(LeaseSocietyEffect.Message(r.data.first))
            }
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.FieldIssues) setState { copy(serverErrors = e.issues, showErrors = true) }
                if (e is AppError.InvalidState || (e is AppError.Server && e.httpCode == 403)) {
                    setState { copy(approval = null) }
                    load()
                }
                e.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(LeaseSocietyEffect.Message(it)) }
            }
        }
    }
}
