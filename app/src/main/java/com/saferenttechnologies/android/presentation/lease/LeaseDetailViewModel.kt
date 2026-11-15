package com.saferenttechnologies.android.presentation.lease

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.LeaseExitRules
import com.saferenttechnologies.android.domain.model.LeaseExitState
import com.saferenttechnologies.android.domain.model.LeaseReviewInfo
import com.saferenttechnologies.android.domain.model.LeaseSummary
import com.saferenttechnologies.android.domain.model.ReviewDraft
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.repository.LeaseActionResult
import com.saferenttechnologies.android.domain.repository.LeaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LeaseSheet { REQUEST, ACCEPT, DECLINE, REVIEW }
enum class LeaseConfirm { SEND, CANCEL }

data class LeaseDetailState(
    val isOpen: Boolean = false,
    val leaseId: String? = null,
    val summary: LeaseSummary? = null,
    val exit: LeaseExitState? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val reviewInfo: LeaseReviewInfo? = null,
    val sheet: LeaseSheet? = null,
    val confirm: LeaseConfirm? = null,
    // "Leave / Close lease" form
    val moveOut: SimpleDate? = null,
    val reason: String = "",
    val reasonError: Boolean = false,
    val addReview: Boolean = false,
    val review: ReviewDraft = ReviewDraft(),
    val reviewError: Boolean = false,
    // Accept / decline note
    val note: String = "",
    val noteError: Boolean = false,
    val busy: Boolean = false,
    val showHistory: Boolean = false,
    /** Review sent with a request / accept → "it will be posted when the lease closes". */
    val reviewSaved: Boolean = false,
) : UiState {
    val isTenant: Boolean get() = exit?.isTenant == true
    val isShortNotice: Boolean
        get() = moveOut?.let { LeaseExitRules.isShortNotice(it, SimpleDate.today(), exit?.noticePeriodDays) } == true
    /** Closed lease, review open and not written yet → "Write your review". */
    val canWriteReview: Boolean get() = exit?.isClosed == true && reviewInfo?.eligible == true && reviewInfo.hasMyReview.not()
}

sealed interface LeaseDetailIntent : UiIntent {
    data class Open(val leaseId: String, val preview: LeaseSummary? = null) : LeaseDetailIntent
    data object Close : LeaseDetailIntent
    data object Refresh : LeaseDetailIntent
    data object ToggleHistory : LeaseDetailIntent

    data object StartRequest : LeaseDetailIntent
    data class PickMoveOut(val date: SimpleDate) : LeaseDetailIntent
    data class ReasonChanged(val text: String) : LeaseDetailIntent
    data class QuickReason(val text: String) : LeaseDetailIntent
    data class ToggleReview(val on: Boolean) : LeaseDetailIntent
    data class Rate(val key: String, val stars: Int) : LeaseDetailIntent
    data class ReviewText(val text: String) : LeaseDetailIntent
    data class ToggleTag(val tag: String) : LeaseDetailIntent
    /** Validates the form → "Send request to close this lease on {date}?" */
    data object SendRequest : LeaseDetailIntent
    data object ConfirmSend : LeaseDetailIntent

    data object AskCancel : LeaseDetailIntent
    data object ConfirmCancel : LeaseDetailIntent
    data object DismissConfirm : LeaseDetailIntent

    data object StartAccept : LeaseDetailIntent
    data object StartDecline : LeaseDetailIntent
    data class NoteChanged(val text: String) : LeaseDetailIntent
    data object SubmitAccept : LeaseDetailIntent
    data object SubmitDecline : LeaseDetailIntent

    data object StartReview : LeaseDetailIntent
    data object SubmitReview : LeaseDetailIntent
    data object DismissSheet : LeaseDetailIntent
}

sealed interface LeaseDetailEffect : UiEffect {
    /** Server message (success or error) → toast. */
    data class Message(val text: String) : LeaseDetailEffect
    /** 403 / 404 → the screen closes. */
    data object Leave : LeaseDetailEffect
    /** Something changed (request sent, accepted, lease closed) → dashboards / lists reload. */
    data object Changed : LeaseDetailEffect
}

@HiltViewModel
class LeaseDetailViewModel @Inject constructor(
    private val repository: LeaseRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<LeaseDetailState, LeaseDetailIntent, LeaseDetailEffect>(LeaseDetailState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { LeaseDetailState() } } }
    }

    override suspend fun handleIntent(intent: LeaseDetailIntent) {
        when (intent) {
            is LeaseDetailIntent.Open -> {
                setState { LeaseDetailState(isOpen = true, leaseId = intent.leaseId, summary = intent.preview, loading = true) }
                load(refreshing = false)
            }
            LeaseDetailIntent.Close -> setState { LeaseDetailState() }
            LeaseDetailIntent.Refresh -> load(refreshing = true)
            LeaseDetailIntent.ToggleHistory -> setState { copy(showHistory = !showHistory) }

            LeaseDetailIntent.StartRequest -> if (currentState.exit?.canRequest == true) setState {
                copy(
                    sheet = LeaseSheet.REQUEST,
                    moveOut = LeaseExitRules.defaultMoveOut(SimpleDate.today(), exit?.noticePeriodDays),
                    reason = "", reasonError = false, addReview = false, review = ReviewDraft(), reviewError = false,
                )
            }
            is LeaseDetailIntent.PickMoveOut -> setState { copy(moveOut = intent.date) }
            is LeaseDetailIntent.ReasonChanged -> setState { copy(reason = intent.text.take(LeaseExitRules.REASON_MAX), reasonError = false) }
            is LeaseDetailIntent.QuickReason -> setState { copy(reason = intent.text, reasonError = false) }
            is LeaseDetailIntent.ToggleReview -> {
                setState { copy(addReview = intent.on, reviewError = false) }
                if (intent.on) loadReviewForm()
            }
            is LeaseDetailIntent.Rate -> setState { copy(review = review.copy(ratings = review.ratings + (intent.key to intent.stars.coerceIn(1, 5))), reviewError = false) }
            is LeaseDetailIntent.ReviewText -> setState { copy(review = review.copy(text = intent.text.take(ReviewDraft.MAX_TEXT))) }
            is LeaseDetailIntent.ToggleTag -> setState { copy(review = review.toggleTag(intent.tag)) }
            LeaseDetailIntent.SendRequest -> {
                val s = currentState
                val reasonBad = !LeaseExitRules.reasonOk(s.reason)
                val reviewBad = s.addReview && !s.review.isComplete
                if (reasonBad || reviewBad || s.moveOut == null) setState { copy(reasonError = reasonBad, reviewError = reviewBad) }
                else setState { copy(confirm = LeaseConfirm.SEND) }
            }
            LeaseDetailIntent.ConfirmSend -> sendRequest()

            LeaseDetailIntent.AskCancel -> if (currentState.exit?.open?.canCancel == true) setState { copy(confirm = LeaseConfirm.CANCEL) }
            LeaseDetailIntent.ConfirmCancel -> {
                val id = currentState.leaseId ?: return
                setState { copy(confirm = null) }
                act { repository.cancelExit(id) }
            }
            LeaseDetailIntent.DismissConfirm -> setState { copy(confirm = null) }

            LeaseDetailIntent.StartAccept -> {
                setState { copy(sheet = LeaseSheet.ACCEPT, note = "", noteError = false, addReview = false, review = ReviewDraft(), reviewError = false) }
            }
            LeaseDetailIntent.StartDecline -> setState { copy(sheet = LeaseSheet.DECLINE, note = "", noteError = false) }
            is LeaseDetailIntent.NoteChanged -> setState { copy(note = intent.text.take(LeaseExitRules.NOTE_MAX), noteError = false) }
            LeaseDetailIntent.SubmitAccept -> {
                val s = currentState
                val id = s.leaseId ?: return
                if (s.addReview && !s.review.isComplete) return setState { copy(reviewError = true) }
                val review = s.review.takeIf { s.addReview }
                act(closeSheet = true, reviewSent = review != null) { repository.acceptExit(id, s.note, review) }
            }
            LeaseDetailIntent.SubmitDecline -> {
                val s = currentState
                val id = s.leaseId ?: return
                if (!LeaseExitRules.declineOk(s.note)) return setState { copy(noteError = true) }
                act(closeSheet = true) { repository.declineExit(id, s.note) }
            }

            LeaseDetailIntent.StartReview -> {
                setState { copy(sheet = LeaseSheet.REVIEW, review = ReviewDraft(), reviewError = false) }
                loadReviewForm()
            }
            LeaseDetailIntent.SubmitReview -> {
                val s = currentState
                val id = s.leaseId ?: return
                if (!s.review.isComplete) return setState { copy(reviewError = true) }
                act(closeSheet = true) { repository.postReview(id, s.review) }
            }
            LeaseDetailIntent.DismissSheet -> if (!currentState.busy) setState { copy(sheet = null, confirm = null) }
        }
    }

    private suspend fun sendRequest() {
        val s = currentState
        val id = s.leaseId ?: return
        val date = s.moveOut ?: return
        setState { copy(confirm = null) }
        val review = s.review.takeIf { s.addReview && it.isComplete }
        act(closeSheet = true, reviewSent = review != null) { repository.requestExit(id, s.reason, date, review) }
    }

    /**
     * Runs one POST (no double submit), toasts the server message, then re-reads GET /exit
     * (and the lease when it closed). Returns true on success.
     */
    private suspend fun act(
        closeSheet: Boolean = false,
        reviewSent: Boolean = false,
        call: suspend () -> AppResult<LeaseActionResult>,
    ): Boolean {
        if (currentState.busy) return false
        setState { copy(busy = true) }
        val r = call()
        setState { copy(busy = false) }
        return when (r) {
            is AppResult.Success -> {
                if (closeSheet) setState { copy(sheet = null, reviewSaved = reviewSaved || reviewSent) }
                if (r.data.message.isNotBlank()) sendEffect(LeaseDetailEffect.Message(r.data.message))
                sendEffect(LeaseDetailEffect.Changed)
                load(refreshing = true)
                true
            }
            is AppResult.Failure -> {
                r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(LeaseDetailEffect.Message(it)) }
                // Field errors (400) keep the sheet open; a stale request (409 / 412) → sheet closes, state reloads.
                val code = (r.error as? AppError.Server)?.httpCode
                if (code == 409 || code == 412) setState { copy(sheet = null) }
                if (code == 403 || code == 404) sendEffect(LeaseDetailEffect.Leave) else load(refreshing = true)
                false
            }
        }
    }

    private suspend fun load(refreshing: Boolean) {
        val id = currentState.leaseId ?: return
        setState { copy(loading = !refreshing && exit == null, refreshing = refreshing, error = null) }
        // The lease header is nice to have: keep the list row's copy when GET leases/{id} fails.
        viewModelScope.launch {
            (repository.lease(id) as? AppResult.Success)?.let { r -> setState { copy(summary = r.data) } }
        }
        when (val r = repository.exitState(id)) {
            is AppResult.Success -> {
                setState { copy(exit = r.data, loading = false, refreshing = false) }
                if (r.data.isClosed) loadReviewInfo()
            }
            is AppResult.Failure -> {
                setState { copy(loading = false, refreshing = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
                val code = (r.error as? AppError.Server)?.httpCode
                if (code == 403 || code == 404) {
                    r.error.message.takeIf { it.isNotBlank() }?.let { sendEffect(LeaseDetailEffect.Message(it)) }
                    sendEffect(LeaseDetailEffect.Leave)
                }
            }
        }
    }

    private suspend fun loadReviewInfo() {
        val id = currentState.leaseId ?: return
        (repository.reviewInfo(id) as? AppResult.Success)?.let { r -> setState { copy(reviewInfo = r.data) } }
    }

    private suspend fun loadReviewForm() {
        if (currentState.reviewInfo == null) loadReviewInfo()
    }
}
