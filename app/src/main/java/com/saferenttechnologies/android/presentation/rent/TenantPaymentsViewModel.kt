package com.saferenttechnologies.android.presentation.rent

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RentReceipt
import com.saferenttechnologies.android.domain.model.TenantPayments
import com.saferenttechnologies.android.domain.repository.ReceiptFiles
import com.saferenttechnologies.android.domain.repository.RentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Tenant: deposit status, pending dues (top), paid history (below), receipts. */
data class TenantPaymentsState(
    val isOpen: Boolean = false,
    val leaseId: String? = null,
    val title: String = "",
    val data: TenantPayments? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Receipt sheet (deposit or a month). */
    val receipt: RentReceipt? = null,
    val receiptFor: String = "",
    val fileBusy: ReceiptAction? = null,
    /** Config payments.enabled → "Pay online" in the pay sheet. */
    val onlineEnabled: Boolean = false,
    // ---- pay sheet ----
    val payDueId: String? = null,
    /** false = choose (online / update paid), true = the "I paid" proof form. */
    val payForm: Boolean = false,
    val payAmount: String = "",
    val payTxn: String = "",
    val payProofUri: String? = null,
    val paySending: Boolean = false,
    val payShowErrors: Boolean = false,
    /** "Pay this month from your deposit?" confirm. */
    val confirmDeposit: Boolean = false,
) : UiState {
    val payDue: com.saferenttechnologies.android.domain.model.RentDue? get() = data?.dues?.firstOrNull { it.id == payDueId }
    val canSendProof: Boolean
        get() = !paySending && com.saferenttechnologies.android.domain.model.PaymentProofRules.canSubmit(payAmount, payTxn, payProofUri != null)
}

sealed interface TenantPaymentsIntent : UiIntent {
    data class Open(val leaseId: String, val title: String) : TenantPaymentsIntent
    data object Close : TenantPaymentsIntent
    data object Refresh : TenantPaymentsIntent
    data class ShowReceipt(val receipt: RentReceipt, val forWhat: String) : TenantPaymentsIntent
    data object HideReceipt : TenantPaymentsIntent
    data class Receipt(val action: ReceiptAction) : TenantPaymentsIntent

    data class StartPay(val dueId: String) : TenantPaymentsIntent
    data object ClosePay : TenantPaymentsIntent
    data object PayOnline : TenantPaymentsIntent
    data object ShowProofForm : TenantPaymentsIntent
    data class PayAmount(val text: String) : TenantPaymentsIntent
    data class PayTxn(val text: String) : TenantPaymentsIntent
    data class PayProof(val uri: String?) : TenantPaymentsIntent
    data object SendProof : TenantPaymentsIntent
    data object AskUseDeposit : TenantPaymentsIntent
    data object CancelUseDeposit : TenantPaymentsIntent
    data object ConfirmUseDeposit : TenantPaymentsIntent
}

@HiltViewModel
class TenantPaymentsViewModel @Inject constructor(
    private val repository: RentRepository,
    private val files: ReceiptFiles,
    private val proofImages: com.saferenttechnologies.android.domain.repository.ProofImages,
    private val config: com.saferenttechnologies.android.domain.repository.ConfigRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<TenantPaymentsState, TenantPaymentsIntent, RentEffect>(TenantPaymentsState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { TenantPaymentsState() } } }
    }

    override suspend fun handleIntent(intent: TenantPaymentsIntent) {
        when (intent) {
            is TenantPaymentsIntent.Open -> {
                setState { TenantPaymentsState(isOpen = true, leaseId = intent.leaseId, title = intent.title) }
                val online = runCatching { config.getPayments().enabled }.getOrDefault(false)
                setState { copy(onlineEnabled = online) }
                load(refreshing = false)
            }
            TenantPaymentsIntent.Close -> setState { TenantPaymentsState() }
            TenantPaymentsIntent.Refresh -> if (currentState.isOpen) { com.saferenttechnologies.android.data.remote.ApiCache.clear(); load(refreshing = true) }
            is TenantPaymentsIntent.ShowReceipt -> setState { copy(receipt = intent.receipt, receiptFor = intent.forWhat) }
            TenantPaymentsIntent.HideReceipt -> if (currentState.fileBusy == null) setState { copy(receipt = null) }
            is TenantPaymentsIntent.Receipt -> receipt(intent.action)

            is TenantPaymentsIntent.StartPay -> currentState.data?.dues?.firstOrNull { it.id == intent.dueId }?.let { due ->
                setState {
                    copy(
                        payDueId = due.id,
                        // No online option → straight to the proof form.
                        payForm = !onlineEnabled,
                        payAmount = due.amount.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: due.amount,
                        payTxn = "",
                        payProofUri = null,
                        payShowErrors = false,
                    )
                }
            }
            TenantPaymentsIntent.ClosePay -> if (!currentState.paySending) setState { copy(payDueId = null) }
            // No payment gateway in the app yet: say so (the proof form is the way for now).
            TenantPaymentsIntent.PayOnline -> sendEffect(RentEffect.Message(ONLINE_SOON))
            TenantPaymentsIntent.ShowProofForm -> setState { copy(payForm = true) }
            is TenantPaymentsIntent.PayAmount -> setState { copy(payAmount = intent.text.filter { it.isDigit() || it == '.' }.take(10)) }
            is TenantPaymentsIntent.PayTxn -> setState { copy(payTxn = intent.text.filterNot { it == '\n' }.take(60)) }
            is TenantPaymentsIntent.PayProof -> setState { copy(payProofUri = intent.uri) }
            TenantPaymentsIntent.SendProof -> sendProof()
            TenantPaymentsIntent.AskUseDeposit -> if (currentState.data?.deposit?.canUseForRent == true) setState { copy(confirmDeposit = true) }
            TenantPaymentsIntent.CancelUseDeposit -> if (!currentState.paySending) setState { copy(confirmDeposit = false) }
            TenantPaymentsIntent.ConfirmUseDeposit -> useDeposit()
        }
    }

    private suspend fun load(refreshing: Boolean) {
        val id = currentState.leaseId ?: return
        setState { copy(loading = !refreshing, refreshing = refreshing, error = null) }
        when (val r = repository.tenantPayments(id)) {
            is AppResult.Success -> setState { copy(data = r.data, loading = false, refreshing = false) }
            is AppResult.Failure -> setState { copy(loading = false, refreshing = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun useDeposit() {
        val dueId = currentState.payDueId ?: return
        if (currentState.paySending) return
        setState { copy(paySending = true) }
        when (val r = repository.useDeposit(dueId)) {
            is AppResult.Success -> {
                setState { copy(paySending = false, confirmDeposit = false, payDueId = null) }
                r.data.takeIf { it.isNotBlank() }?.let { sendEffect(RentEffect.Message(it)) }
                load(refreshing = true)
            }
            is AppResult.Failure -> {
                setState { copy(paySending = false, confirmDeposit = false) }
                r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(RentEffect.Message(it)) }
            }
        }
    }

    private suspend fun sendProof() {
        val s = currentState
        val dueId = s.payDueId ?: return
        if (!s.canSendProof) return setState { copy(payShowErrors = true) }
        setState { copy(paySending = true) }
        val bytes = withContext(Dispatchers.IO) { s.payProofUri?.let(proofImages::readJpeg) }
        if (bytes == null) {
            setState { copy(paySending = false) }
            return sendEffect(RentEffect.Message(PROOF_UNREADABLE))
        }
        when (val r = repository.requestPaymentUpdate(dueId, s.payAmount, s.payTxn, bytes)) {
            is AppResult.Success -> {
                setState { copy(paySending = false, payDueId = null) }
                r.data.takeIf { it.isNotBlank() }?.let { sendEffect(RentEffect.Message(it)) }
                load(refreshing = true)
            }
            is AppResult.Failure -> {
                setState { copy(paySending = false) }
                r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(RentEffect.Message(it)) }
            }
        }
    }

    private suspend fun receipt(action: ReceiptAction) {
        val receipt = currentState.receipt ?: return
        if (currentState.fileBusy != null) return
        setState { copy(fileBusy = action) }
        when (val r = repository.receiptPdf(receipt.id)) {
            is AppResult.Success -> {
                val name = "SafeRent-receipt-${receipt.number.ifBlank { receipt.id }.replace(Regex("[^A-Za-z0-9-]"), "")}.pdf"
                withContext(Dispatchers.IO) {
                    when (action) {
                        ReceiptAction.VIEW -> sendEffect(RentEffect.ViewPdf(files.cache(name, r.data)))
                        ReceiptAction.SHARE -> sendEffect(RentEffect.SharePdf(files.cache(name, r.data)))
                        ReceiptAction.DOWNLOAD -> if (files.saveToDownloads(name, r.data)) sendEffect(RentEffect.Saved)
                        else sendEffect(RentEffect.SharePdf(files.cache(name, r.data)))
                    }
                }
            }
            is AppResult.Failure -> r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(RentEffect.Message(it)) }
        }
        setState { copy(fileBusy = null) }
    }

    companion object {
        const val ONLINE_SOON = "@online_soon"
        const val PROOF_UNREADABLE = "@proof_unreadable"
    }
}
