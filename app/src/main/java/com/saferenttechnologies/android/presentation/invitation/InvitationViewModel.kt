package com.saferenttechnologies.android.presentation.invitation

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.DocType
import com.saferenttechnologies.android.domain.model.DocumentOption
import com.saferenttechnologies.android.domain.model.DocumentVault
import com.saferenttechnologies.android.domain.model.InviteAnswerRules
import com.saferenttechnologies.android.domain.model.TenantAnswers
import com.saferenttechnologies.android.domain.model.TenantInvitation
import com.saferenttechnologies.android.domain.repository.InvitationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class InvitationOutcome { ACCEPTED, DECLINED }

data class InvitationState(
    val isOpen: Boolean = false,
    val token: String? = null,
    val loading: Boolean = false,
    val invitation: TenantInvitation? = null,
    /** Load failed: the server's message (404 / not your invite / network). */
    val error: String? = null,
    val notFound: Boolean = false,
    /** "I've read the terms" — Accept stays disabled until ticked. */
    val agreed: Boolean = false,
    val accepting: Boolean = false,
    val declining: Boolean = false,
    val confirmDecline: Boolean = false,
    val declineReason: String = "",
    val outcome: InvitationOutcome? = null,
    val outcomeMessage: String = "",
    /** Snackbar (accept / decline failed). */
    val message: String? = null,
    /** "A few details for the owner" open (the invitation asks something). In memory only. */
    val answering: Boolean = false,
    val answers: TenantAnswers = TenantAnswers(),
    val showErrors: Boolean = false,
    /** 400 details.issues: path → message. */
    val serverErrors: Map<String, String> = emptyMap(),
    /** After accepting: documents the owner asked for → "Share documents with your owner". */
    val shareDocs: List<DocumentOption> = emptyList(),
    val leaseId: String? = null,
    val vault: DocumentVault? = null,
    val sharing: Boolean = false,
    val shareDone: Boolean = false,
) : UiState {
    val busy: Boolean get() = accepting || declining
    val canAccept: Boolean get() = invitation?.isPending == true && agreed && !busy
    val problems: Map<String, InviteAnswerRules.Problem>
        get() = invitation?.requirements?.let { InviteAnswerRules.validate(it, answers) }.orEmpty()
    /** Share step shown after accepting. */
    val sharingStep: Boolean get() = shareDocs.isNotEmpty() && leaseId != null && !shareDone
}

sealed interface InvitationIntent : UiIntent {
    data class Open(val token: String) : InvitationIntent
    data object Close : InvitationIntent
    data object Retry : InvitationIntent
    data class Agree(val agreed: Boolean) : InvitationIntent
    data object Accept : InvitationIntent
    data object AskDecline : InvitationIntent
    data object DismissDecline : InvitationIntent
    data class DeclineReason(val text: String) : InvitationIntent
    data object ConfirmDecline : InvitationIntent
    data object MessageShown : InvitationIntent
    data class EditAnswers(val answers: TenantAnswers) : InvitationIntent
    data object SubmitAnswers : InvitationIntent
    data object CloseAnswers : InvitationIntent
    data class ToggleShare(val documentId: String, val share: Boolean) : InvitationIntent
    data class AddDocument(val option: DocumentOption) : InvitationIntent
    data object RefreshVault : InvitationIntent
    data object FinishSharing : InvitationIntent
}

sealed interface InvitationEffect : UiEffect {
    /** Accepted / declined → home + tenant dashboard reload (new lease card). */
    data object Changed : InvitationEffect
    /** "Add" a requested document → the document vault upload (type pre-picked when known). */
    data class OpenUpload(val type: DocType?) : InvitationEffect
}

@HiltViewModel
class InvitationViewModel @Inject constructor(
    private val repository: InvitationRepository,
    private val documents: com.saferenttechnologies.android.domain.repository.DocumentsRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<InvitationState, InvitationIntent, InvitationEffect>(InvitationState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { InvitationState() } } }
    }

    override suspend fun handleIntent(intent: InvitationIntent) {
        when (intent) {
            is InvitationIntent.Open -> {
                setState { InvitationState(isOpen = true, token = intent.token) }
                load()
            }
            InvitationIntent.Close -> setState { InvitationState() }
            InvitationIntent.Retry -> load()
            is InvitationIntent.Agree -> setState { copy(agreed = intent.agreed) }
            InvitationIntent.Accept -> accept()
            InvitationIntent.AskDecline -> if (!currentState.busy) setState { copy(confirmDecline = true) }
            InvitationIntent.DismissDecline -> setState { copy(confirmDecline = false, declineReason = "") }
            is InvitationIntent.DeclineReason -> setState { copy(declineReason = intent.text.take(500)) }
            InvitationIntent.ConfirmDecline -> decline()
            InvitationIntent.MessageShown -> setState { copy(message = null) }
            is InvitationIntent.EditAnswers -> if (!currentState.accepting) setState { copy(answers = clean(intent.answers), serverErrors = emptyMap()) }
            InvitationIntent.CloseAnswers -> if (!currentState.accepting) setState { copy(answering = false) }
            InvitationIntent.SubmitAnswers -> submitAnswers()
            is InvitationIntent.ToggleShare -> toggleShare(intent.documentId, intent.share)
            is InvitationIntent.AddDocument -> {
                val type = currentState.vault?.types?.firstOrNull { it.key.uppercase() in intent.option.vaultTypes }
                sendEffect(InvitationEffect.OpenUpload(type))
            }
            InvitationIntent.RefreshVault -> loadVault()
            InvitationIntent.FinishSharing -> setState { copy(shareDone = true) }
        }
    }

    private fun clean(a: TenantAnswers) = a.copy(
        companyName = a.companyName.take(120),
        adults = a.adults.filter(Char::isDigit).take(2),
        children = a.children.filter(Char::isDigit).take(2),
        petDetails = a.petDetails.take(120),
        stayMonths = a.stayMonths.filter(Char::isDigit).take(3),
        references = a.references.map { it.copy(name = it.name.take(120), relation = it.relation.take(40), phone = it.phone.take(20)) },
    )

    private suspend fun submitAnswers() {
        val s = currentState
        val token = s.token ?: return
        val req = s.invitation?.requirements ?: return
        if (s.accepting) return
        if (s.problems.isNotEmpty()) return setState { copy(showErrors = true) }
        setState { copy(accepting = true, serverErrors = emptyMap()) }
        when (val r = repository.accept(token, s.answers, req)) {
            is AppResult.Success -> accepted(r.data)
            is AppResult.Failure -> {
                val e = r.error
                if (e is AppError.FieldIssues) {
                    setState { copy(accepting = false, serverErrors = e.issues, showErrors = true, message = e.issues.values.firstOrNull() ?: e.message) }
                } else {
                    failed(e)
                }
            }
        }
    }

    private suspend fun accepted(d: com.saferenttechnologies.android.domain.model.InvitationDecision) {
        // Answers leave memory once sent.
        setState {
            copy(
                accepting = false, answering = false, answers = TenantAnswers(), outcome = InvitationOutcome.ACCEPTED, outcomeMessage = d.message,
                shareDocs = d.documentsToShare, leaseId = d.leaseId,
            )
        }
        sendEffect(InvitationEffect.Changed)
        if (d.documentsToShare.isNotEmpty() && d.leaseId != null) loadVault()
    }

    private suspend fun loadVault() {
        if (currentState.leaseId == null) return
        (documents.vault() as? AppResult.Success)?.let { r -> setState { copy(vault = r.data) } }
    }

    private suspend fun toggleShare(docId: String, share: Boolean) {
        val lease = currentState.leaseId ?: return
        if (currentState.sharing) return
        setState { copy(sharing = true) }
        val r = if (share) documents.share(docId, lease) else documents.unshare(docId, lease)
        setState { copy(sharing = false) }
        when (r) {
            is AppResult.Success -> setState { copy(vault = r.data) }
            is AppResult.Failure -> setState { copy(message = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun load() {
        val token = currentState.token ?: return
        setState { copy(loading = true, error = null, notFound = false) }
        when (val r = repository.invitation(token)) {
            is AppResult.Success -> setState { copy(loading = false, invitation = r.data) }
            is AppResult.Failure -> setState {
                copy(
                    loading = false,
                    notFound = (r.error as? AppError.Server)?.httpCode == 404,
                    error = r.error.takeUnless { it is AppError.AccountBlocked }?.message.orEmpty(),
                )
            }
        }
    }

    private suspend fun accept() {
        val token = currentState.token ?: return
        if (!currentState.canAccept) return
        // The owner asks something → "A few details for the owner" first (pre-filled with my last answers).
        val req = currentState.invitation?.requirements
        if (req != null) {
            if (!currentState.answering) setState {
                copy(answering = true, answers = if (answers == TenantAnswers()) InviteAnswerRules.start(req, invitation?.prefill) else answers, showErrors = false)
            }
            return
        }
        setState { copy(accepting = true) }
        when (val r = repository.accept(token)) {
            is AppResult.Success -> accepted(r.data)
            is AppResult.Failure -> failed(r.error)
        }
    }

    private suspend fun decline() {
        val token = currentState.token ?: return
        setState { copy(declining = true) }
        when (val r = repository.reject(token, currentState.declineReason)) {
            is AppResult.Success -> {
                setState { copy(declining = false, confirmDecline = false, outcome = InvitationOutcome.DECLINED, outcomeMessage = r.data.message) }
                sendEffect(InvitationEffect.Changed)
            }
            is AppResult.Failure -> failed(r.error)
        }
    }

    /** 409 / 410 (already used, revoked, expired meanwhile) → reload so the screen shows the real status. */
    private suspend fun failed(error: AppError) {
        setState { copy(accepting = false, declining = false, confirmDecline = false, message = error.takeUnless { it is AppError.AccountBlocked }?.message) }
        val code = (error as? AppError.Server)?.httpCode
        if (code == 409 || code == 410 || code == 404) load()
    }
}
