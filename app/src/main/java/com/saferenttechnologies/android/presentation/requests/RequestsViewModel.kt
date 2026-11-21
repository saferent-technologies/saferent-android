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
import com.saferenttechnologies.android.domain.model.RequestInvoice
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.WorkDetails
import com.saferenttechnologies.android.domain.repository.RequestMedia
import com.saferenttechnologies.android.domain.repository.RequestResult
import com.saferenttechnologies.android.domain.repository.RequestUpdate
import com.saferenttechnologies.android.domain.repository.RequestsRepository
import com.saferenttechnologies.android.domain.repository.UploadFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RequestsMode { LIST, RAISE, DETAIL }
enum class RequestSheet { RESOLVE, REOPEN, CANCEL }

/** Where a recorded video goes. */
enum class MediaTarget { RAISE, SHEET, UPDATE, ADD }

/** "Raise a request" form; photos = picked / taken photo and video uris (photos as JPEG, videos 720p MP4 on send). */
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
    /** Resolve: who paid (OWNER / TENANT / SHARED / NO_COST) + amount; sheetPhotos = receipts. */
    val sheetPaidBy: String? = null,
    val sheetAmount: String = "",
    // landlord update panel
    val updStatus: String? = null,
    val updRemark: String = "",
    val updPhotos: List<String> = emptyList(),
    /** Chosen "In progress": work start date (prefilled today). */
    val updStartDate: String? = null,
    /** Chosen "Resolved": who paid (optional for the owner) + actual cost. */
    val updPaidBy: String? = null,
    val updAmount: String = "",
    // owner "Work details" sheet
    val work: WorkDetails? = null,
    val workErrors: Boolean = false,
    val busy: Boolean = false,
    /** Video being made smaller (0..100) / files uploading (0..100). */
    val preparing: Int? = null,
    val uploading: Int? = null,
    /** In-app video recorder open for … */
    val recordFor: MediaTarget? = null,
    /** Full-screen photos (urls) + the one tapped. */
    val viewer: Pair<List<String>, Int>? = null,
    /** Full-screen video (url). */
    val playing: String? = null,
    /** Invoice PDF being fetched. */
    val openingInvoice: String? = null,
) : UiState {
    val hasMore: Boolean get() = page < totalPages
    val tabs: List<RequestTab> get() = if (landlord) RequestTab.LANDLORD else RequestTab.TENANT
    val canSend: Boolean get() = !busy && RequestRules.canSend(raise.type, raise.category, raise.description)
    val canSendUpdate: Boolean get() = !busy && detail?.let {
        RequestRules.canUpdate(it.status, updStatus, updRemark) &&
            (updStatus != "RESOLVED" || RequestRules.resolveProblem(updPaidBy, updAmount, payerRequired = false) == null)
    } == true
    val resolveProblem: RequestRules.ResolveProblem? get() = RequestRules.resolveProblem(sheetPaidBy, sheetAmount)
    val workProblems: Set<String> get() = work?.let { workProblemsOf(it) }.orEmpty()
}

/** "technicianName" / "technicianPhone" / "estimatedCost" / "workStartDate" with a problem. */
fun workProblemsOf(w: WorkDetails): Set<String> = buildSet {
    if (w.technicianName.trim().length > RequestRules.TECHNICIAN_MAX) add("technicianName")
    if (!RequestRules.phoneOk(w.technicianPhone)) add("technicianPhone")
    if (w.technicianPhone.isNotBlank() && w.technicianName.isBlank()) add("technicianName")
    RequestRules.cost(w.estimatedCost)?.let { if (it.isNaN()) add("estimatedCost") }
    if (!RequestRules.workStartOk(w.workStartDate)) add("workStartDate")
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
    data class UpdStartDate(val date: String?) : RequestsIntent
    data class UpdPaidBy(val paidBy: String?) : RequestsIntent
    data class UpdAmount(val text: String) : RequestsIntent

    // resolve sheet
    data class SheetPaidBy(val paidBy: String) : RequestsIntent
    data class SheetAmount(val text: String) : RequestsIntent

    // owner work details
    data object OpenWork : RequestsIntent
    data object CloseWork : RequestsIntent
    data class EditWork(val work: WorkDetails) : RequestsIntent
    data object SaveWork : RequestsIntent

    // invoices
    data class AddInvoice(val uris: List<String>) : RequestsIntent
    data class OpenInvoice(val invoice: RequestInvoice) : RequestsIntent

    // video
    data class RecordVideo(val target: MediaTarget) : RequestsIntent
    data class VideoRecorded(val path: String) : RequestsIntent
    data object CloseRecorder : RequestsIntent
    data class PlayVideo(val url: String?) : RequestsIntent

    data class ViewPhotos(val urls: List<String>, val index: Int) : RequestsIntent
    data object CloseViewer : RequestsIntent
}

sealed interface RequestsEffect : UiEffect {
    data class Message(val text: String) : RequestsEffect
    /** content:// uri of a downloaded invoice PDF. */
    data class OpenPdf(val uri: String) : RequestsEffect
}

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val repository: RequestsRepository,
    private val media: RequestMedia,
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
            is RequestsIntent.RaiseAddPhotos -> limited(currentState.raise.photos, intent.uris).let { l -> setState { copy(raise = raise.copy(photos = l)) } }
            is RequestsIntent.RaiseRemovePhoto -> setState { copy(raise = raise.copy(photos = raise.photos.filterIndexed { i, _ -> i != intent.index })) }
            RequestsIntent.RaiseSend -> raise()

            is RequestsIntent.Select -> {
                setState { copy(mode = RequestsMode.DETAIL, detail = items.firstOrNull { it.id == intent.id }) }
                loadDetail(intent.id)
            }
            RequestsIntent.Remind -> currentState.detail?.let { d -> act { repository.remind(d.id) } }
            is RequestsIntent.ShowSheet -> setState { copy(sheet = intent.sheet, sheetRemark = "", sheetPhotos = emptyList(), sheetError = false, sheetPaidBy = null, sheetAmount = "") }
            RequestsIntent.DismissSheet -> if (!currentState.busy) setState { copy(sheet = null) }
            is RequestsIntent.SheetRemark -> setState { copy(sheetRemark = intent.text.take(1000), sheetError = false) }
            is RequestsIntent.SheetAddPhotos -> {
                val s = currentState
                // Resolve: receipts (photos / PDFs). Reopen: photos / videos.
                val l = if (s.sheet == RequestSheet.RESOLVE) (s.sheetPhotos + intent.uris).distinct().take(RequestRules.MAX_PHOTOS) else limited(s.sheetPhotos, intent.uris)
                setState { copy(sheetPhotos = l) }
            }
            is RequestsIntent.SheetRemovePhoto -> setState { copy(sheetPhotos = sheetPhotos.filterIndexed { i, _ -> i != intent.index }) }
            RequestsIntent.SheetSubmit -> submitSheet()
            is RequestsIntent.AddPhotos -> currentState.detail?.let { d ->
                if (intent.uris.isEmpty() || currentState.busy) return
                upload(limited(emptyList(), intent.uris)) { files, progress -> repository.addPhotos(d.id, files, progress) }
            }

            is RequestsIntent.UpdStatus -> setState {
                copy(
                    updStatus = intent.status,
                    // "In progress" → work starts today (changeable), unless it has a start date already.
                    updStartDate = if (intent.status == "IN_PROGRESS" && detail?.workStartedAt == null) updStartDate ?: SimpleDate.today().iso() else null,
                )
            }
            is RequestsIntent.UpdRemark -> setState { copy(updRemark = intent.text.take(1000)) }
            is RequestsIntent.UpdAddPhotos -> limited(currentState.updPhotos, intent.uris).let { l -> setState { copy(updPhotos = l) } }
            is RequestsIntent.UpdRemovePhoto -> setState { copy(updPhotos = updPhotos.filterIndexed { i, _ -> i != intent.index }) }
            RequestsIntent.SendUpdate -> sendUpdate()
            is RequestsIntent.UpdStartDate -> setState { copy(updStartDate = intent.date) }
            is RequestsIntent.UpdPaidBy -> setState { copy(updPaidBy = intent.paidBy, updAmount = if (intent.paidBy == "NO_COST") "" else updAmount) }
            is RequestsIntent.UpdAmount -> setState { copy(updAmount = amountInput(intent.text)) }

            is RequestsIntent.SheetPaidBy -> setState { copy(sheetPaidBy = intent.paidBy, sheetAmount = if (intent.paidBy == "NO_COST") "" else sheetAmount, sheetError = false) }
            is RequestsIntent.SheetAmount -> setState { copy(sheetAmount = amountInput(intent.text), sheetError = false) }

            RequestsIntent.OpenWork -> currentState.detail?.let { d -> setState { copy(work = WorkDetails.of(d), workErrors = false) } }
            RequestsIntent.CloseWork -> if (!currentState.busy) setState { copy(work = null) }
            is RequestsIntent.EditWork -> setState {
                copy(work = intent.work.copy(technicianName = intent.work.technicianName.take(RequestRules.TECHNICIAN_MAX), estimatedCost = amountInput(intent.work.estimatedCost)))
            }
            RequestsIntent.SaveWork -> saveWork()

            is RequestsIntent.AddInvoice -> currentState.detail?.let { d ->
                if (intent.uris.isEmpty() || currentState.busy) return
                upload(intent.uris.take(RequestRules.MAX_PHOTOS)) { files, progress -> repository.addInvoice(d.id, files, progress) }
            }
            is RequestsIntent.OpenInvoice -> openInvoice(intent.invoice)

            is RequestsIntent.RecordVideo -> setState { copy(recordFor = intent.target) }
            RequestsIntent.CloseRecorder -> setState { copy(recordFor = null) }
            is RequestsIntent.VideoRecorded -> {
                val target = currentState.recordFor
                setState { copy(recordFor = null) }
                when (target) {
                    MediaTarget.RAISE -> handleIntent(RequestsIntent.RaiseAddPhotos(listOf(intent.path)))
                    MediaTarget.SHEET -> handleIntent(RequestsIntent.SheetAddPhotos(listOf(intent.path)))
                    MediaTarget.UPDATE -> handleIntent(RequestsIntent.UpdAddPhotos(listOf(intent.path)))
                    MediaTarget.ADD -> handleIntent(RequestsIntent.AddPhotos(listOf(intent.path)))
                    null -> Unit
                }
            }
            is RequestsIntent.PlayVideo -> setState { copy(playing = intent.url) }

            is RequestsIntent.ViewPhotos -> if (intent.urls.isNotEmpty()) setState { copy(viewer = intent.urls to intent.index.coerceIn(0, intent.urls.lastIndex)) }
            RequestsIntent.CloseViewer -> setState { copy(viewer = null) }
        }
    }

    private fun fresh(landlord: Boolean) = RequestsState(isOpen = true, landlord = landlord, summary = currentState.summary, options = currentState.options)

    private suspend fun back() {
        val s = currentState
        when {
            s.recordFor != null -> setState { copy(recordFor = null) }
            s.playing != null -> setState { copy(playing = null) }
            s.viewer != null -> setState { copy(viewer = null) }
            s.work != null -> if (!s.busy) setState { copy(work = null) }
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
            is AppResult.Success -> setState { copy(detail = r.data, detailLoading = false, updStatus = null, updStartDate = null, updPaidBy = null, updAmount = "") }
            is AppResult.Failure -> {
                setState { copy(detailLoading = false) }
                r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(RequestsEffect.Message(it)) }
                if ((r.error as? AppError.Server)?.httpCode in setOf(403, 404)) back()
            }
        }
    }

    /** Adds picked / recorded media: at most 10 files and [RequestRules.MAX_VIDEOS] videos per upload. */
    private fun limited(current: List<String>, added: List<String>): List<String> {
        var videos = current.count { media.isVideo(it) }
        val out = current.toMutableList()
        var dropped = false
        for (u in added) {
            if (u in out) continue
            if (out.size >= RequestRules.MAX_PHOTOS) { dropped = true; break }
            if (media.isVideo(u)) {
                if (videos >= RequestRules.MAX_VIDEOS) { dropped = true; continue }
                videos++
            }
            out += u
        }
        if (dropped) sendEffect(RequestsEffect.Message(if (videos >= RequestRules.MAX_VIDEOS) MEDIA_TWO_VIDEOS else MEDIA_LIMIT))
        return out
    }

    /** Digits and one dot, 2 decimals. */
    private fun amountInput(text: String): String {
        val clean = text.filter { it.isDigit() || it == '.' }
        val dot = clean.indexOf('.')
        return if (dot < 0) clean.take(8) else clean.substring(0, dot).take(8) + "." + clean.substring(dot + 1).replace(".", "").take(2)
    }

    /** Uris → upload files (videos made 720p first, with progress); null + message on a problem. */
    private suspend fun files(uris: List<String>): List<UploadFile>? {
        if (uris.isEmpty()) return emptyList()
        if (uris.any { media.isVideo(it) }) setState { copy(preparing = 0) }
        val r = media.prepare(uris) { p -> setState { copy(preparing = p) } }
        setState { copy(preparing = null) }
        return when (r) {
            is RequestMedia.Result.Ok -> r.files
            is RequestMedia.Result.Failed -> {
                sendEffect(
                    RequestsEffect.Message(
                        when (r.problem) {
                            RequestMedia.Problem.UNREADABLE -> PHOTO_UNREADABLE
                            RequestMedia.Problem.VIDEO_TOO_BIG -> MEDIA_VIDEO_BIG
                            RequestMedia.Problem.FILE_TOO_BIG -> MEDIA_FILE_BIG
                            RequestMedia.Problem.TOO_MANY_VIDEOS -> MEDIA_TWO_VIDEOS
                            RequestMedia.Problem.NOT_A_VIDEO -> MEDIA_NOT_VIDEO
                        },
                    ),
                )
                null
            }
        }
    }

    /** Prepare + upload with progress; temp videos removed after a successful send. */
    private suspend fun upload(
        uris: List<String>,
        closeSheet: Boolean = false,
        call: suspend (List<UploadFile>, (Int) -> Unit) -> AppResult<RequestResult>,
    ): Boolean {
        if (currentState.busy) return false
        setState { copy(busy = true) }
        val files = files(uris)
        setState { copy(busy = false) }
        if (files == null) return false
        val progress: (Int) -> Unit = if (files.isEmpty()) { _ -> } else { p -> setState { copy(uploading = p) } }
        val ok = act(closeSheet) { call(files, progress) }
        setState { copy(uploading = null) }
        if (ok) media.cleanUp(files)
        return ok
    }

    private suspend fun raise() {
        val s = currentState
        val f = s.raise
        if (!RequestRules.canSend(f.type, f.category, f.description)) return setState { copy(raise = raise.copy(showErrors = true)) }
        setState { copy(busy = true) }
        val files = files(f.photos) ?: return setState { copy(busy = false) }
        val r = repository.create(f.type!!, f.category!!, f.description, f.priority, null, files) { p -> if (files.isNotEmpty()) setState { copy(uploading = p) } }
        setState { copy(busy = false, uploading = null) }
        if (r is AppResult.Success) media.cleanUp(files)
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
            RequestSheet.RESOLVE -> {
                if (s.resolveProblem != null) return setState { copy(sheetError = true) }
                val paidBy = s.sheetPaidBy ?: return
                val amount = RequestRules.resolveAmount(paidBy, s.sheetAmount)
                // No cost → no receipts.
                val receipts = if (paidBy == "NO_COST") emptyList() else s.sheetPhotos
                upload(receipts, closeSheet = true) { files, progress -> repository.resolve(d.id, paidBy, amount, s.sheetRemark, files, progress) }
            }
            RequestSheet.CANCEL -> act(closeSheet = true) { repository.cancel(d.id, s.sheetRemark) }
            RequestSheet.REOPEN -> {
                if (!RequestRules.remarkOk(s.sheetRemark)) return setState { copy(sheetError = true) }
                upload(s.sheetPhotos, closeSheet = true) { files, progress -> repository.reopen(d.id, s.sheetRemark, files, progress) }
            }
            null -> Unit
        }
    }

    private suspend fun sendUpdate() {
        val s = currentState
        val d = s.detail ?: return
        if (!s.canSendUpdate) return
        val status = s.updStatus?.takeIf { it != d.status }
        val update = RequestUpdate(
            status = status,
            remark = s.updRemark,
            workStartDate = s.updStartDate.takeIf { status == "IN_PROGRESS" },
            paidBy = s.updPaidBy.takeIf { status == "RESOLVED" },
            actualCost = if (status == "RESOLVED") RequestRules.resolveAmount(s.updPaidBy, s.updAmount) else null,
        )
        if (upload(s.updPhotos) { files, progress -> repository.update(d.id, update, files, progress) }) {
            setState { copy(updStatus = null, updRemark = "", updPhotos = emptyList(), updStartDate = null, updPaidBy = null, updAmount = "") }
        }
    }

    /** Owner "Work details": sends technician / estimate / start date (cleared ones as null). */
    private suspend fun saveWork() {
        val s = currentState
        val d = s.detail ?: return
        val w = s.work ?: return
        if (s.workProblems.isNotEmpty()) return setState { copy(workErrors = true) }
        if (w == WorkDetails.of(d)) return setState { copy(work = null) }
        val update = RequestUpdate(
            sendWork = true,
            technicianName = w.technicianName.trim().ifBlank { null },
            technicianPhone = RequestRules.phoneForApi(w.technicianPhone),
            estimatedCost = RequestRules.cost(w.estimatedCost),
            workStartDate = w.workStartDate,
        )
        if (act { repository.update(d.id, update, emptyList()) }) setState { copy(work = null) }
    }

    /** Photo → viewer; PDF → download (signed in) to cache → system PDF viewer. */
    private suspend fun openInvoice(inv: RequestInvoice) {
        if (!inv.isPdf) return setState { copy(viewer = listOf(com.saferenttechnologies.android.presentation.property.absoluteUrl(inv.url)) to 0) }
        if (currentState.openingInvoice != null) return
        setState { copy(openingInvoice = inv.id) }
        val r = repository.invoicePdf(inv.url)
        setState { copy(openingInvoice = null) }
        when (r) {
            is AppResult.Success -> sendEffect(RequestsEffect.OpenPdf(r.data))
            is AppResult.Failure -> r.error.takeUnless { it is AppError.AccountBlocked }?.message?.let { sendEffect(RequestsEffect.Message(it)) }
        }
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
        const val MEDIA_TWO_VIDEOS = "@media_two_videos"
        const val MEDIA_LIMIT = "@media_limit"
        const val MEDIA_VIDEO_BIG = "@media_video_big"
        const val MEDIA_FILE_BIG = "@media_file_big"
        const val MEDIA_NOT_VIDEO = "@media_not_video"
    }
}
