package com.saferenttechnologies.android.presentation.rent

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RentBucket
import com.saferenttechnologies.android.domain.model.RentDue
import com.saferenttechnologies.android.domain.repository.ReceiptFiles
import com.saferenttechnologies.android.domain.repository.RentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ReceiptAction { VIEW, DOWNLOAD, SHARE }

data class RentState(
    val isOpen: Boolean = false,
    val bucket: RentBucket = RentBucket.EXPECTED,
    /** "2026-11" from the dashboard summary (null → the server's current month). */
    val period: String? = null,
    val items: List<RentDue> = emptyList(),
    val total: String = "",
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Receipt sheet open for this due. */
    val receiptDueId: String? = null,
    /** PDF being fetched (View / Download / Share). */
    val fileBusy: ReceiptAction? = null,
    /** Reminder being sent for this due. */
    val remindingId: String? = null,
    /** Reminded in this session (button shows "Reminder sent"). */
    val reminded: Set<String> = emptySet(),
) : UiState {
    val receiptDue: RentDue? get() = items.firstOrNull { it.id == receiptDueId }
}

sealed interface RentIntent : UiIntent {
    data class Open(val bucket: RentBucket, val period: String?) : RentIntent
    data object Close : RentIntent
    data class SelectBucket(val bucket: RentBucket) : RentIntent
    data object Refresh : RentIntent
    data class OpenReceipt(val dueId: String) : RentIntent
    data object CloseReceipt : RentIntent
    data class Receipt(val action: ReceiptAction) : RentIntent
    data class Remind(val dueId: String) : RentIntent
}

sealed interface RentEffect : UiEffect {
    data class Message(val text: String) : RentEffect
    /** content:// uri of the cached PDF → open in a viewer / share sheet. */
    data class ViewPdf(val uri: String) : RentEffect
    data class SharePdf(val uri: String) : RentEffect
    /** Saved in Downloads. */
    data object Saved : RentEffect
}

@HiltViewModel
class RentViewModel @Inject constructor(
    private val repository: RentRepository,
    private val files: ReceiptFiles,
    sessionEvents: SessionEvents,
) : MviViewModel<RentState, RentIntent, RentEffect>(RentState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { RentState() } } }
    }

    override suspend fun handleIntent(intent: RentIntent) {
        when (intent) {
            is RentIntent.Open -> {
                setState { RentState(isOpen = true, bucket = intent.bucket, period = intent.period?.ifBlank { null }) }
                load(refreshing = false)
            }
            RentIntent.Close -> setState { RentState() }
            is RentIntent.SelectBucket -> if (intent.bucket != currentState.bucket) {
                setState { copy(bucket = intent.bucket, items = emptyList(), total = "", receiptDueId = null) }
                load(refreshing = false)
            }
            RentIntent.Refresh -> if (currentState.isOpen) load(refreshing = true)
            is RentIntent.OpenReceipt -> setState { copy(receiptDueId = intent.dueId) }
            RentIntent.CloseReceipt -> if (currentState.fileBusy == null) setState { copy(receiptDueId = null) }
            is RentIntent.Receipt -> receipt(intent.action)
            is RentIntent.Remind -> remind(intent.dueId)
        }
    }

    private suspend fun load(refreshing: Boolean) {
        setState { copy(loading = !refreshing, refreshing = refreshing, error = null) }
        val bucket = currentState.bucket
        when (val r = repository.dues(bucket, currentState.period)) {
            is AppResult.Success -> if (currentState.bucket == bucket) {
                setState { copy(items = r.data.items, total = r.data.total, loading = false, refreshing = false) }
            }
            is AppResult.Failure -> setState { copy(loading = false, refreshing = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun remind(dueId: String) {
        if (currentState.remindingId != null || dueId in currentState.reminded) return
        setState { copy(remindingId = dueId) }
        when (val r = repository.remind(dueId)) {
            is AppResult.Success -> {
                setState { copy(remindingId = null, reminded = reminded + dueId) }
                r.data.takeIf { it.isNotBlank() }?.let { sendEffect(RentEffect.Message(it)) }
            }
            is AppResult.Failure -> {
                setState { copy(remindingId = null) }
                r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(RentEffect.Message(it)) }
            }
        }
    }

    /** Fetch the PDF once per action, then view / save / share it. */
    private suspend fun receipt(action: ReceiptAction) {
        val receipt = currentState.receiptDue?.receipt ?: return
        if (currentState.fileBusy != null) return
        setState { copy(fileBusy = action) }
        when (val r = repository.receiptPdf(receipt.id)) {
            is AppResult.Success -> {
                val name = "SafeRent-receipt-${receipt.number.ifBlank { receipt.id }.replace(Regex("[^A-Za-z0-9-]"), "")}.pdf"
                withContext(Dispatchers.IO) {
                    when (action) {
                        ReceiptAction.VIEW -> sendEffect(RentEffect.ViewPdf(files.cache(name, r.data)))
                        ReceiptAction.SHARE -> sendEffect(RentEffect.SharePdf(files.cache(name, r.data)))
                        // Android 9 and older: no Downloads without a storage permission → share sheet ("Save to …").
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
