package com.saferenttechnologies.android.presentation.rent

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RentTransaction
import com.saferenttechnologies.android.domain.repository.ReceiptFiles
import com.saferenttechnologies.android.domain.repository.RentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Landlord: every rent payment / refund, newest first, load-more paging. */
data class TransactionsState(
    val isOpen: Boolean = false,
    val items: List<RentTransaction> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Detail sheet for this transaction. */
    val openId: String? = null,
    val fileBusy: ReceiptAction? = null,
) : UiState {
    val hasMore: Boolean get() = page < totalPages
    val open: RentTransaction? get() = items.firstOrNull { it.id == openId }
}

sealed interface TransactionsIntent : UiIntent {
    data object Open : TransactionsIntent
    data object Close : TransactionsIntent
    data object Refresh : TransactionsIntent
    data object LoadMore : TransactionsIntent
    data class Select(val id: String) : TransactionsIntent
    data object Dismiss : TransactionsIntent
    data class Receipt(val action: ReceiptAction) : TransactionsIntent
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: RentRepository,
    private val files: ReceiptFiles,
    sessionEvents: SessionEvents,
) : MviViewModel<TransactionsState, TransactionsIntent, RentEffect>(TransactionsState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { TransactionsState() } } }
    }

    override suspend fun handleIntent(intent: TransactionsIntent) {
        when (intent) {
            TransactionsIntent.Open -> {
                setState { TransactionsState(isOpen = true) }
                load(1, refreshing = false)
            }
            TransactionsIntent.Close -> setState { TransactionsState() }
            TransactionsIntent.Refresh -> if (currentState.isOpen) { com.saferenttechnologies.android.data.remote.ApiCache.clear(); load(1, refreshing = true) }
            TransactionsIntent.LoadMore -> if (currentState.hasMore && !currentState.loading) load(currentState.page + 1, refreshing = false)
            is TransactionsIntent.Select -> setState { copy(openId = intent.id) }
            TransactionsIntent.Dismiss -> if (currentState.fileBusy == null) setState { copy(openId = null) }
            is TransactionsIntent.Receipt -> receipt(intent.action)
        }
    }

    private suspend fun load(page: Int, refreshing: Boolean) {
        setState { copy(loading = !refreshing, refreshing = refreshing, error = null) }
        when (val r = repository.transactions(page)) {
            is AppResult.Success -> setState {
                copy(
                    items = if (page == 1) r.data.items else (items + r.data.items).distinctBy { it.id },
                    page = r.data.page,
                    totalPages = r.data.totalPages,
                    loading = false,
                    refreshing = false,
                )
            }
            is AppResult.Failure -> setState { copy(loading = false, refreshing = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun receipt(action: ReceiptAction) {
        val receipt = currentState.open?.receipt ?: return
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
}
