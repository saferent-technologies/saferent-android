package com.saferenttechnologies.android.presentation.lease

import com.saferenttechnologies.android.domain.model.PaymentProofRules
import com.saferenttechnologies.android.domain.model.DepositInfo
import com.saferenttechnologies.android.domain.repository.RentRepository
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

enum class LeaseSheet { REQUEST, ACCEPT, DECLINE, REVIEW, DEPOSIT, UNDO, UNDO_DECLINE }
enum class LeaseConfirm { SEND, CANCEL, UNDO_CANCEL, UNDO_ACCEPT }

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
    /** Security deposit (GET leases/{id}/payments); landlord marks it received. */
    val deposit: DepositInfo? = null,
    val depositAmount: String = "",
    val depositDate: SimpleDate? = null,
) : UiState {
    val isLandlord: Boolean get() = exit?.side.equals("LANDLORD", true)
    val isTenant: Boolean get() = exit?.isTenant == true
    val isShortNotice: Boolean
        get() = moveOut?.let { LeaseExitRules.isShortNotice(it, SimpleDate.today(), exit?.noticePeriodDays) } == true
    /** Closed lease, review open and not written yet → "Write your review". */
    val canWriteReview: Boolean get() = exit?.isClosed == true && reviewInfo?.eligible == true && reviewInfo.hasMyReview.not()
}

sealed interface LeaseDetailIntent : UiIntent {
    /** [startReview] = closed lease → open "Write your review" right away (landlord banner / tenant). */
    data class Open(val leaseId: String, val preview: LeaseSummary? = null, val startReview: Boolean = false) : LeaseDetailIntent
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

    /** Agreed closing → "Keep the lease" (reason) / other side agrees or declines / I withdraw it. */
    data object StartUndo : LeaseDetailIntent
    data object SubmitUndo : LeaseDetailIntent
    data object AskAcceptUndo : LeaseDetailIntent
    data object StartDeclineUndo : LeaseDetailIntent
    data object SubmitDeclineUndo : LeaseDetailIntent
    data object AskCancelUndo : LeaseDetailIntent

    data object StartDeposit : LeaseDetailIntent
    data class DepositAmount(val text: String) : LeaseDetailIntent
    data class DepositDate(val date: SimpleDate) : LeaseDetailIntent
    data object SaveDeposit : LeaseDetailIntent
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
    private val rent: RentRepository,
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
                if (intent.startReview && currentState.canWriteReview) handleIntent(LeaseDetailIntent.StartReview)
            }
            LeaseDetailIntent.Close -> setState { LeaseDetailState() }
            LeaseDetailIntent.Refresh -> { com.saferenttechnologies.android.data.remote.ApiCache.clear(); load(refreshing = true) }
            LeaseDetailIntent.ToggleHistory -> setState { copy(showHistory = !showHistory) }

            // No review here: it's asked once the lease has closed (tenant: before using the app; landlord: before the next invite).
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
                val reviewBad = false
                if (reasonBad || reviewBad || s.moveOut == null) setState { copy(reasonError = reasonBad, reviewError = reviewBad) }
                else setState { copy(confirm = LeaseConfirm.SEND) }
            }
            LeaseDetailIntent.ConfirmSend -> sendRequest()

            LeaseDetailIntent.AskCancel -> if (currentState.exit?.open?.canCancel == true) setState { copy(confirm = LeaseConfirm.CANCEL) }
            LeaseDetailIntent.ConfirmCancel -> {
                val id = currentState.leaseId ?: return
                val kind = currentState.confirm
                setState { copy(confirm = null) }
                when (kind) {
                    LeaseConfirm.UNDO_CANCEL -> act { repository.cancelUndo(id) }
                    LeaseConfirm.UNDO_ACCEPT -> act { repository.acceptUndo(id) }
                    else -> act { repository.cancelExit(id) }
                }
            }
            LeaseDetailIntent.DismissConfirm -> setState { copy(confirm = null) }

            LeaseDetailIntent.StartUndo -> if (currentState.exit?.open?.canUndo == true) setState { copy(sheet = LeaseSheet.UNDO, reason = "", reasonError = false) }
            LeaseDetailIntent.SubmitUndo -> {
                val s = currentState
                val id = s.leaseId ?: return
                if (!LeaseExitRules.reasonOk(s.reason)) return setState { copy(reasonError = true) }
                act(closeSheet = true) { repository.requestUndo(id, s.reason) }
            }
            LeaseDetailIntent.AskAcceptUndo -> setState { copy(confirm = LeaseConfirm.UNDO_ACCEPT) }
            LeaseDetailIntent.StartDeclineUndo -> setState { copy(sheet = LeaseSheet.UNDO_DECLINE, note = "", noteError = false) }
            LeaseDetailIntent.SubmitDeclineUndo -> {
                val s = currentState
                val id = s.leaseId ?: return
                if (!LeaseExitRules.declineOk(s.note)) return setState { copy(noteError = true) }
                act(closeSheet = true) { repository.declineUndo(id, s.note) }
            }
            LeaseDetailIntent.AskCancelUndo -> setState { copy(confirm = LeaseConfirm.UNDO_CANCEL) }

            LeaseDetailIntent.StartAccept -> setState { copy(sheet = LeaseSheet.ACCEPT, note = "", noteError = false, addReview = false, reviewError = false) }
            LeaseDetailIntent.StartDecline -> setState { copy(sheet = LeaseSheet.DECLINE, note = "", noteError = false) }
            is LeaseDetailIntent.NoteChanged -> setState { copy(note = intent.text.take(LeaseExitRules.NOTE_MAX), noteError = false) }
            LeaseDetailIntent.SubmitAccept -> {
                val s = currentState
                val id = s.leaseId ?: return
                act(closeSheet = true) { repository.acceptExit(id, s.note, null) }
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

            LeaseDetailIntent.StartDeposit -> if (currentState.isLandlord) setState {
                copy(
                    sheet = LeaseSheet.DEPOSIT,
                    depositAmount = (deposit?.amount?.ifBlank { null } ?: summary?.securityDeposit.orEmpty())
                        .toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    depositDate = SimpleDate.today(),
                )
            }
            is LeaseDetailIntent.DepositAmount -> setState { copy(depositAmount = intent.text.filter { it.isDigit() || it == '.' }.take(10)) }
            is LeaseDetailIntent.DepositDate -> setState { copy(depositDate = intent.date) }
            LeaseDetailIntent.SaveDeposit -> {
                val s = currentState
                val id = s.leaseId ?: return
                val date = s.depositDate ?: return
                if (!PaymentProofRules.amountOk(s.depositAmount)) return
                act(closeSheet = true) { rent.recordDeposit(id, s.depositAmount, date).let { r -> if (r is AppResult.Success) AppResult.Success(LeaseActionResult(r.data)) else r as AppResult.Failure } }
            }
        }
    }

    private suspend fun sendRequest() {
        val s = currentState
        val id = s.leaseId ?: return
        val date = s.moveOut ?: return
        setState { copy(confirm = null) }
        act(closeSheet = true) { repository.requestExit(id, s.reason, date, null) }
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
        viewModelScope.launch {
            (rent.tenantPayments(id) as? AppResult.Success)?.let { r -> setState { copy(deposit = r.data.deposit) } }
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

    /** The review is required → never leave the form spinning: no form from the server = just "Overall". */
    private suspend fun loadReviewForm() {
        if (currentState.reviewInfo == null) loadReviewInfo()
        if (currentState.reviewInfo == null) setState { copy(reviewInfo = LeaseReviewInfo()) }
    }
}
