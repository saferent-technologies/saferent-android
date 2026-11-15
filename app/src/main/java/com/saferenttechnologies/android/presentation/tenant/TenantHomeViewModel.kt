package com.saferenttechnologies.android.presentation.tenant

import com.saferenttechnologies.android.domain.model.PaymentProofRules
import com.saferenttechnologies.android.domain.model.DepositRefund
import com.saferenttechnologies.android.domain.model.ReviewDraft
import com.saferenttechnologies.android.domain.model.ReviewForm
import com.saferenttechnologies.android.domain.model.PendingReview
import com.saferenttechnologies.android.domain.model.TenantPayments
import com.saferenttechnologies.android.domain.repository.RentRepository
import com.saferenttechnologies.android.domain.repository.LeaseRepository
import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.TenantApplication
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import com.saferenttechnologies.android.domain.usecase.GetTenantHomeStateUseCase
import com.saferenttechnologies.android.domain.usecase.TenantHomeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TenantHomeUiState(
    val loading: Boolean = false,
    val home: TenantHomeState? = null,
    val error: String? = null,
    /** Rentals waiting for a review (blocks applying until done). */
    val pendingReviews: Int = 0,
    /** Linked tenant tapped "Explore homes". */
    val exploring: Boolean = false,
    val hasApplications: Boolean = false,
    /** The landlord asked to close my lease → "Close requested" on the lease card. */
    val closeRequested: Boolean = false,
    /** Deposit + dues of the active lease (Payments box on the dashboard). */
    val payments: TenantPayments? = null,
    /** Ended leases still to review → the review screen comes before Explore / the dashboard. */
    val reviewQueue: List<PendingReview> = emptyList(),
    val gate: ReviewGate = ReviewGate(),
) : UiState {
    val showExplore: Boolean get() = home == TenantHomeState.Explore || exploring
}

sealed interface TenantHomeIntent : UiIntent {
    /** Tenant dashboard shown — loads once per session. */
    data object Start : TenantHomeIntent
    data object Refresh : TenantHomeIntent
    data object OpenExplore : TenantHomeIntent
    data object CloseExplore : TenantHomeIntent
    data class GateRate(val key: String, val stars: Int) : TenantHomeIntent
    data class GateText(val text: String) : TenantHomeIntent
    data class GateTag(val tag: String) : TenantHomeIntent
    data class GateRefund(val answer: DepositRefund) : TenantHomeIntent
    data class GateRefundAmount(val text: String) : TenantHomeIntent
    data object GateSubmit : TenantHomeIntent
}

/** The required "review your last rental" screen (one lease at a time). */
data class ReviewGate(
    val form: ReviewForm? = null,
    val draft: ReviewDraft = ReviewDraft(),
    val refund: DepositRefund? = null,
    val refundAmount: String = "",
    val showErrors: Boolean = false,
    val busy: Boolean = false,
) {
    fun ready(depositAsked: Boolean): Boolean = draft.isComplete &&
        (!depositAsked || (refund != null && (refund != DepositRefund.PARTIAL || PaymentProofRules.amountOk(refundAmount))))
}

sealed interface TenantHomeEffect : UiEffect {
    data class Message(val text: String) : TenantHomeEffect
}

/** Tenant start: active lease → dashboard placeholder; none → Explore. Cached for the session. */
@HiltViewModel
class TenantHomeViewModel @Inject constructor(
    private val getHome: GetTenantHomeStateUseCase,
    private val repository: BrowseRepository,
    private val leases: LeaseRepository,
    private val rent: RentRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<TenantHomeUiState, TenantHomeIntent, TenantHomeEffect>(TenantHomeUiState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { TenantHomeUiState() } } }
    }

    override suspend fun handleIntent(intent: TenantHomeIntent) {
        when (intent) {
            TenantHomeIntent.Start -> if (currentState.home == null && !currentState.loading) load()
            TenantHomeIntent.Refresh -> { com.saferenttechnologies.android.data.remote.ApiCache.clear(); load() }
            TenantHomeIntent.OpenExplore -> setState { copy(exploring = true) }
            TenantHomeIntent.CloseExplore -> setState { copy(exploring = false) }
            is TenantHomeIntent.GateRate -> setState { copy(gate = gate.copy(draft = gate.draft.copy(ratings = gate.draft.ratings + (intent.key to intent.stars.coerceIn(1, 5))))) }
            is TenantHomeIntent.GateText -> setState { copy(gate = gate.copy(draft = gate.draft.copy(text = intent.text.take(ReviewDraft.MAX_TEXT)))) }
            is TenantHomeIntent.GateTag -> setState { copy(gate = gate.copy(draft = gate.draft.toggleTag(intent.tag))) }
            is TenantHomeIntent.GateRefund -> setState { copy(gate = gate.copy(refund = intent.answer)) }
            is TenantHomeIntent.GateRefundAmount -> setState { copy(gate = gate.copy(refundAmount = intent.text.filter { it.isDigit() || it == '.' }.take(10))) }
            TenantHomeIntent.GateSubmit -> submitGate()
        }
    }

    private suspend fun load() {
        setState { copy(loading = true, error = null) }
        loadReviewQueue()
        viewModelScope.launch {
            (repository.pendingReviews() as? AppResult.Success)?.let { r -> setState { copy(pendingReviews = r.data) } }
        }
        viewModelScope.launch {
            val any = listOf("PENDING", "APPROVED").any { s ->
                ((repository.myApplications(s) as? AppResult.Success<List<TenantApplication>>)?.data?.isNotEmpty() == true)
            }
            setState { copy(hasApplications = any) }
        }
        when (val r = getHome()) {
            is AppResult.Success -> {
                setState { copy(loading = false, home = r.data) }
                val leaseId = (r.data as? TenantHomeState.Linked)?.lease?.id
                if (leaseId != null) viewModelScope.launch {
                    (rent.tenantPayments(leaseId) as? AppResult.Success)?.let { p -> setState { copy(payments = p.data) } }
                }
                val asked = leaseId != null && (leases.exitState(leaseId) as? AppResult.Success)?.data?.needsMyResponse == true
                setState { copy(closeRequested = asked) }
            }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    // ---------------- required review after a lease ends ----------------

    private suspend fun loadReviewQueue() {
        val queue = (leases.pendingReviews() as? AppResult.Success)?.data ?: return
        setState { copy(reviewQueue = queue) }
        queue.firstOrNull()?.let { loadGateForm(it.leaseId) }
    }

    private suspend fun loadGateForm(leaseId: String) {
        val form = (leases.reviewInfo(leaseId) as? AppResult.Success)?.data?.form ?: ReviewForm()
        setState { copy(gate = ReviewGate(form = form)) }
    }

    /** Review (+ the deposit answer) → next ended lease, or on to the app. */
    private suspend fun submitGate() {
        val s = currentState
        val due = s.reviewQueue.firstOrNull() ?: return
        val depositAsked = due.asksDeposit
        if (!s.gate.ready(depositAsked)) return setState { copy(gate = gate.copy(showErrors = true)) }
        setState { copy(gate = gate.copy(busy = true)) }
        val review = leases.postReview(due.leaseId, s.gate.draft)
        if (review is AppResult.Failure) {
            setState { copy(gate = gate.copy(busy = false)) }
            review.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(TenantHomeEffect.Message(it)) }
            return
        }
        s.gate.refund?.let { answer ->
            val r = leases.depositRefund(due.leaseId, answer, s.gate.refundAmount)
            if (r is AppResult.Failure) {
                // The review is in; only the deposit answer failed → stay on this screen and retry that.
                setState { copy(gate = gate.copy(busy = false)) }
                r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(TenantHomeEffect.Message(it)) }
                return
            }
        }
        val rest = s.reviewQueue.drop(1)
        setState { copy(reviewQueue = rest, gate = ReviewGate()) }
        (review as? AppResult.Success)?.data?.message?.takeIf { it.isNotBlank() }?.let { sendEffect(TenantHomeEffect.Message(it)) }
        rest.firstOrNull()?.let { loadGateForm(it.leaseId) } ?: load()
    }
}
