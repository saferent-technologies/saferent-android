package com.saferenttechnologies.android.presentation.requests

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RequestOptions
import com.saferenttechnologies.android.domain.model.RequestRules
import com.saferenttechnologies.android.domain.model.RequestSummary
import com.saferenttechnologies.android.domain.model.RequestTab
import com.saferenttechnologies.android.domain.model.ServiceRequest
import com.saferenttechnologies.android.domain.repository.ProofImages
import com.saferenttechnologies.android.domain.repository.RequestResult
import com.saferenttechnologies.android.domain.repository.RequestsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class RequestsMode { LIST, RAISE, DETAIL }
enum class RequestSheet { RESOLVE, REOPEN, CANCEL }

/** "Raise a request" form; photos = picked / taken image uris (uploaded as JPEG on send). */
data class RaiseForm(
    val type: String? = null,
    val category: String? = null,
    val description: String = "",
    val priority: String = "MEDIUM",
    val photos: List<String> = emptyList(),
    val showErrors: Boolean = false,
)

data class RequestsState(
    val isOpen: Boolean = false,
    val mode: RequestsMode = RequestsMode.LIST,
    val landlord: Boolean = false,
    /** Opened straight on a request / the raise form (push, dashboard) → Back closes everything. */
    val direct: Boolean = false,
    val tab: RequestTab = RequestTab.ACTIVE,
    val items: List<ServiceRequest> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val options: RequestOptions? = null,
    /** Badges: landlord needsAction, tenant myOpen. */
    val summary: RequestSummary = RequestSummary(),
    // raise
    val raise: RaiseForm = RaiseForm(),
    /** "Green Heights · Flat 302" — the rental the request is for. */
    val rentalTitle: String = "",
    // detail
    val detail: ServiceRequest? = null,
    val detailLoading: Boolean = false,
    val sheet: RequestSheet? = null,
    val sheetRemark: String = "",
    val sheetPhotos: List<String> = emptyList(),
    val sheetError: Boolean = false,
    // landlord update panel
    val updStatus: String? = null,
    val updRemark: String = "",
    val updPhotos: List<String> = emptyList(),
    val busy: Boolean = false,
    /** Full-screen photos (urls) + the one tapped. */
    val viewer: Pair<List<String>, Int>? = null,
) : UiState {
    val hasMore: Boolean get() = page < totalPages
    val tabs: List<RequestTab> get() = if (landlord) RequestTab.LANDLORD else RequestTab.TENANT
    val canSend: Boolean get() = !busy && RequestRules.canSend(raise.type, raise.category, raise.description)
    val canSendUpdate: Boolean get() = !busy && detail?.let { RequestRules.canUpdate(it.status, updStatus, updRemark) } == true
}

sealed interface RequestsIntent : UiIntent {
    data class Open(val landlord: Boolean, val tab: RequestTab? = null) : RequestsIntent
    data class OpenRaise(val rentalTitle: String) : RequestsIntent
    data class OpenDetail(val id: String, val landlord: Boolean) : RequestsIntent
    data object Back : RequestsIntent
    data object Close : RequestsIntent
    data class SelectTab(val tab: RequestTab) : RequestsIntent
    data object Refresh : RequestsIntent
    data object LoadMore : RequestsIntent
    data class RefreshSummary(val landlord: Boolean) : RequestsIntent
    data object StartRaise : RequestsIntent

    data class RaiseType(val type: String) : RequestsIntent
    data class RaiseCategory(val category: String) : RequestsIntent
    data class RaiseDescription(val text: String) : RequestsIntent
    data class RaisePriority(val priority: String) : RequestsIntent
    data class RaiseAddPhotos(val uris: List<String>) : RequestsIntent
    data class RaiseRemovePhoto(val index: Int) : RequestsIntent
    data object RaiseSend : RequestsIntent

    data class Select(val id: String) : RequestsIntent
    data object Remind : RequestsIntent
    data class ShowSheet(val sheet: RequestSheet) : RequestsIntent
    data object DismissSheet : RequestsIntent
    data class SheetRemark(val text: String) : RequestsIntent
    data class SheetAddPhotos(val uris: List<String>) : RequestsIntent
    data class SheetRemovePhoto(val index: Int) : RequestsIntent
    data object SheetSubmit : RequestsIntent
    data class AddPhotos(val uris: List<String>) : RequestsIntent

    data class UpdStatus(val status: String) : RequestsIntent
    data class UpdRemark(val text: String) : RequestsIntent
    data class UpdAddPhotos(val uris: List<String>) : RequestsIntent
    data class UpdRemovePhoto(val index: Int) : RequestsIntent
    data object SendUpdate : RequestsIntent

    data class ViewPhotos(val urls: List<String>, val index: Int) : RequestsIntent
    data object CloseViewer : RequestsIntent
}

sealed interface RequestsEffect : UiEffect {
    data class Message(val text: String) : RequestsEffect
}

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val repository: RequestsRepository,
    private val images: ProofImages,
    sessionEvents: SessionEvents,
) : MviViewModel<RequestsState, RequestsIntent, RequestsEffect>(RequestsState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { RequestsState() } } }
    }

    override suspend fun handleIntent(intent: RequestsIntent) {
        when (intent) {
            is RequestsIntent.Open -> {
                setState { fresh(landlord = intent.landlord).copy(tab = intent.tab ?: RequestTab.ALL) }
                loadOptions()
                load(1, refreshing = false)
            }
            is RequestsIntent.OpenRaise -> {
                setState { fresh(landlord = false).copy(mode = RequestsMode.RAISE, direct = true, rentalTitle = intent.rentalTitle) }
                loadOptions()
            }
            is RequestsIntent.OpenDetail -> {
                setState { fresh(landlord = intent.landlord).copy(mode = RequestsMode.DETAIL, direct = true) }
                loadOptions()
                loadDetail(intent.id)
            }
            RequestsIntent.Back -> back()
            RequestsIntent.Close -> setState { RequestsState(summary = summary) }
            is RequestsIntent.SelectTab -> if (intent.tab != currentState.tab) {
                setState { copy(tab = intent.tab, items = emptyList(), page = 0, totalPages = 1) }
                load(1, refreshing = false)
            }
            RequestsIntent.Refresh -> when (currentState.mode.also { com.saferenttechnologies.android.data.remote.ApiCache.clear() }) {
                RequestsMode.DETAIL -> currentState.detail?.id?.let { loadDetail(it) }
                else -> load(1, refreshing = true)
            }
            RequestsIntent.LoadMore -> if (currentState.hasMore && !currentState.loading) load(currentState.page + 1, refreshing = false)
            is RequestsIntent.RefreshSummary -> loadSummary(intent.landlord)
            RequestsIntent.StartRaise -> setState { copy(mode = RequestsMode.RAISE, raise = RaiseForm()) }

            is RequestsIntent.RaiseType -> setState { copy(raise = raise.copy(type = intent.type, category = null)) }
            is RequestsIntent.RaiseCategory -> setState { copy(raise = raise.copy(category = intent.category)) }
            is RequestsIntent.RaiseDescription -> setState { copy(raise = raise.copy(description = intent.text.take(RequestRules.DESCRIPTION_MAX))) }
            is RequestsIntent.RaisePriority -> setState { copy(raise = raise.copy(priority = intent.priority)) }
            is RequestsIntent.RaiseAddPhotos -> setState { copy(raise = raise.copy(photos = (raise.photos + intent.uris).distinct().take(RequestRules.MAX_PHOTOS))) }
            is RequestsIntent.RaiseRemovePhoto -> setState { copy(raise = raise.copy(photos = raise.photos.filterIndexed { i, _ -> i != intent.index })) }
            RequestsIntent.RaiseSend -> raise()

            is RequestsIntent.Select -> {
                setState { copy(mode = RequestsMode.DETAIL, detail = items.firstOrNull { it.id == intent.id }) }
                loadDetail(intent.id)
            }
            RequestsIntent.Remind -> currentState.detail?.let { d -> act { repository.remind(d.id) } }
            is RequestsIntent.ShowSheet -> setState { copy(sheet = intent.sheet, sheetRemark = "", sheetPhotos = emptyList(), sheetError = false) }
            RequestsIntent.DismissSheet -> if (!currentState.busy) setState { copy(sheet = null) }
            is RequestsIntent.SheetRemark -> setState { copy(sheetRemark = intent.text.take(1000), sheetError = false) }
            is RequestsIntent.SheetAddPhotos -> setState { copy(sheetPhotos = (sheetPhotos + intent.uris).distinct().take(RequestRules.MAX_PHOTOS)) }
            is RequestsIntent.SheetRemovePhoto -> setState { copy(sheetPhotos = sheetPhotos.filterIndexed { i, _ -> i != intent.index }) }
            RequestsIntent.SheetSubmit -> submitSheet()
            is RequestsIntent.AddPhotos -> currentState.detail?.let { d ->
                if (intent.uris.isEmpty()) return
                val photos = jpegs(intent.uris) ?: return
                act { repository.addPhotos(d.id, photos) }
            }

            is RequestsIntent.UpdStatus -> setState { copy(updStatus = intent.status) }
            is RequestsIntent.UpdRemark -> setState { copy(updRemark = intent.text.take(1000)) }
            is RequestsIntent.UpdAddPhotos -> setState { copy(updPhotos = (updPhotos + intent.uris).distinct().take(RequestRules.MAX_PHOTOS)) }
            is RequestsIntent.UpdRemovePhoto -> setState { copy(updPhotos = updPhotos.filterIndexed { i, _ -> i != intent.index }) }
            RequestsIntent.SendUpdate -> sendUpdate()

            is RequestsIntent.ViewPhotos -> if (intent.urls.isNotEmpty()) setState { copy(viewer = intent.urls to intent.index.coerceIn(0, intent.urls.lastIndex)) }
            RequestsIntent.CloseViewer -> setState { copy(viewer = null) }
        }
    }

    private fun fresh(landlord: Boolean) = RequestsState(isOpen = true, landlord = landlord, summary = currentState.summary, options = currentState.options)

    private suspend fun back() {
        val s = currentState
        when {
            s.viewer != null -> setState { copy(viewer = null) }
            s.sheet != null -> if (!s.busy) setState { copy(sheet = null) }
            s.direct || s.mode == RequestsMode.LIST -> setState { RequestsState(summary = summary) }
            else -> {
                setState { copy(mode = RequestsMode.LIST, detail = null) }
                load(1, refreshing = true)
            }
        }
    }

    private suspend fun loadOptions() {
        if (currentState.options != null) return
        (repository.options() as? AppResult.Success)?.let { r -> setState { copy(options = r.data) } }
    }

    private suspend fun loadSummary(landlord: Boolean) {
        (repository.summary() as? AppResult.Success)?.let { r -> setState { copy(summary = r.data) } }
    }

    private suspend fun load(page: Int, refreshing: Boolean) {
        val s = currentState
        setState { copy(loading = !refreshing, refreshing = refreshing, error = null) }
        when (val r = repository.list(s.tab.status, s.landlord, page)) {
            is AppResult.Success -> setState {
                copy(
                    items = if (page == 1) r.data.items else (items + r.data.items).distinctBy { it.id },
                    page = r.data.page,
                    totalPages = r.data.totalPages,
                    loading = false,
                    refreshing = false,
                    summary = if (landlord) summary.copy(needsAction = r.data.needsAction.takeIf { it > 0 } ?: summary.needsAction) else summary,
                )
            }
            is AppResult.Failure -> setState { copy(loading = false, refreshing = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    private suspend fun loadDetail(id: String) {
        setState { copy(detailLoading = true) }
        when (val r = repository.detail(id)) {
            is AppResult.Success -> setState { copy(detail = r.data, detailLoading = false, updStatus = null) }
            is AppResult.Failure -> {
                setState { copy(detailLoading = false) }
                r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(RequestsEffect.Message(it)) }
                if ((r.error as? AppError.Server)?.httpCode in setOf(403, 404)) back()
            }
        }
    }

    /** Picked images → JPEGs (≤ 1600 px); null + message when one can't be read. */
    private suspend fun jpegs(uris: List<String>): List<ByteArray>? {
        val out = withContext(Dispatchers.IO) { uris.map { images.readJpeg(it) } }
        if (out.any { it == null }) {
            sendEffect(RequestsEffect.Message(PHOTO_UNREADABLE))
            return null
        }
        return out.filterNotNull()
    }

    private suspend fun raise() {
        val s = currentState
        val f = s.raise
        if (!RequestRules.canSend(f.type, f.category, f.description)) return setState { copy(raise = raise.copy(showErrors = true)) }
        setState { copy(busy = true) }
        val photos = jpegs(f.photos) ?: return setState { copy(busy = false) }
        val r = repository.create(f.type!!, f.category!!, f.description, f.priority, null, photos)
        setState { copy(busy = false) }
        when (r) {
            is AppResult.Success -> {
                r.data.message.takeIf { it.isNotBlank() }?.let { sendEffect(RequestsEffect.Message(it)) }
                loadSummary(false)
                val created = r.data.request
                if (created != null) setState { copy(mode = RequestsMode.DETAIL, detail = created, raise = RaiseForm()) }
                else setState { copy(mode = RequestsMode.LIST, raise = RaiseForm()) }.also { load(1, refreshing = true) }
            }
            is AppResult.Failure -> r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(RequestsEffect.Message(it)) }
        }
    }

    private suspend fun submitSheet() {
        val s = currentState
        val d = s.detail ?: return
        when (s.sheet) {
            RequestSheet.RESOLVE -> act(closeSheet = true) { repository.resolve(d.id, s.sheetRemark) }
            RequestSheet.CANCEL -> act(closeSheet = true) { repository.cancel(d.id, s.sheetRemark) }
            RequestSheet.REOPEN -> {
                if (!RequestRules.remarkOk(s.sheetRemark)) return setState { copy(sheetError = true) }
                setState { copy(busy = true) }
                val photos = jpegs(s.sheetPhotos) ?: return setState { copy(busy = false) }
                setState { copy(busy = false) }
                act(closeSheet = true) { repository.reopen(d.id, s.sheetRemark, photos) }
            }
            null -> Unit
        }
    }

    private suspend fun sendUpdate() {
        val s = currentState
        val d = s.detail ?: return
        if (!s.canSendUpdate) return
        setState { copy(busy = true) }
        val photos = jpegs(s.updPhotos) ?: return setState { copy(busy = false) }
        setState { copy(busy = false) }
        val status = s.updStatus?.takeIf { it != d.status }
        if (act { repository.update(d.id, status, s.updRemark, photos) }) setState { copy(updStatus = null, updRemark = "", updPhotos = emptyList()) }
    }

    /** One action at a time → message, the returned request replaces the screen, badges refresh. 412 → reload. */
    private suspend fun act(closeSheet: Boolean = false, call: suspend () -> AppResult<RequestResult>): Boolean {
        if (currentState.busy) return false
        setState { copy(busy = true) }
        val r = call()
        setState { copy(busy = false) }
        return when (r) {
            is AppResult.Success -> {
                if (closeSheet) setState { copy(sheet = null) }
                r.data.message.takeIf { it.isNotBlank() }?.let { sendEffect(RequestsEffect.Message(it)) }
                r.data.request?.let { req -> setState { copy(detail = req, items = items.map { if (it.id == req.id) req else it }) } }
                    ?: currentState.detail?.id?.let { loadDetail(it) }
                loadSummary(currentState.landlord)
                true
            }
            is AppResult.Failure -> {
                r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(RequestsEffect.Message(it)) }
                val code = (r.error as? AppError.Server)?.httpCode
                if (code == 412 || code == 409 || code == 429) currentState.detail?.id?.let { loadDetail(it) }
                false
            }
        }
    }

    companion object {
        const val PHOTO_UNREADABLE = "@photo_unreadable"
    }
}
