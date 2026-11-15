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
) : UiState {
    val busy: Boolean get() = accepting || declining
    val canAccept: Boolean get() = invitation?.isPending == true && agreed && !busy
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
}

sealed interface InvitationEffect : UiEffect {
    /** Accepted / declined → home + tenant dashboard reload (new lease card). */
    data object Changed : InvitationEffect
}

@HiltViewModel
class InvitationViewModel @Inject constructor(
    private val repository: InvitationRepository,
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
        setState { copy(accepting = true) }
        when (val r = repository.accept(token)) {
            is AppResult.Success -> {
                setState { copy(accepting = false, outcome = InvitationOutcome.ACCEPTED, outcomeMessage = r.data.message) }
                sendEffect(InvitationEffect.Changed)
            }
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
