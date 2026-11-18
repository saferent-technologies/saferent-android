package com.saferenttechnologies.android.presentation.documents

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
import com.saferenttechnologies.android.domain.model.DocumentRules
import com.saferenttechnologies.android.domain.model.DocumentVault
import com.saferenttechnologies.android.domain.model.SharedDocuments
import com.saferenttechnologies.android.domain.model.TenantDoc
import com.saferenttechnologies.android.domain.repository.DocumentsRepository
import com.saferenttechnologies.android.domain.repository.ProofImages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Upload sheet: type + page photos (uris, sent as JPEG). */
data class UploadForm(
    val type: DocType? = null,
    val pages: List<String> = emptyList(),
    /** A PDF instead of photos (uri + its file name). */
    val pdf: String? = null,
    val pdfName: String = "",
)

/** Document opened full screen: photos (urls) or a PDF (bytes, rendered on the phone, never saved). */
data class DocViewer(val images: List<String> = emptyList(), val start: Int = 0, val pdfLoading: Boolean = false, val pdf: ByteArray? = null) {
    override fun equals(other: Any?) = other is DocViewer && images == other.images && start == other.start &&
        pdfLoading == other.pdfLoading && (pdf === other.pdf || (pdf != null && other.pdf != null && pdf.contentEquals(other.pdf)))
    override fun hashCode() = images.hashCode() * 31 + start + (pdf?.size ?: 0)
}

data class DocumentsState(
    val isOpen: Boolean = false,
    /** null = tenant's own vault; set = landlord looking at what this lease's tenant shared. */
    val leaseId: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val vault: DocumentVault = DocumentVault(),
    val shared: SharedDocuments? = null,
    /** Landlord: 403 / lease ended → "no longer available". */
    val accessEnded: Boolean = false,
    val upload: UploadForm? = null,
    /** Document whose share options are open. */
    val shareFor: String? = null,
    val confirmDelete: String? = null,
    val busy: Boolean = false,
    val viewer: DocViewer? = null,
) : UiState {
    val landlord: Boolean get() = leaseId != null
    val addable: List<DocType> get() = DocumentRules.addable(vault)
    val canUpload: Boolean get() = !busy && DocumentRules.canUpload(upload?.type, upload?.pages?.size ?: 0, hasPdf = upload?.pdf != null)
    fun doc(id: String?): TenantDoc? = vault.documents.firstOrNull { it.id == id }
}

sealed interface DocumentsIntent : UiIntent {
    data object OpenMine : DocumentsIntent
    data class OpenShared(val leaseId: String) : DocumentsIntent
    data object Close : DocumentsIntent
    data object Refresh : DocumentsIntent
    data object StartUpload : DocumentsIntent
    data class PickType(val type: DocType) : DocumentsIntent
    data class AddPages(val uris: List<String>) : DocumentsIntent
    data class RemovePage(val index: Int) : DocumentsIntent
    data class PickPdf(val uri: String) : DocumentsIntent
    data object RemovePdf : DocumentsIntent
    data object SendUpload : DocumentsIntent
    data object DismissUpload : DocumentsIntent
    data class ShowShare(val docId: String?) : DocumentsIntent
    data class ToggleShare(val docId: String, val leaseId: String) : DocumentsIntent
    data class AskDelete(val docId: String?) : DocumentsIntent
    data object ConfirmDelete : DocumentsIntent
    data class View(val doc: TenantDoc, val page: Int = 0) : DocumentsIntent
    data object CloseViewer : DocumentsIntent
}

sealed interface DocumentsEffect : UiEffect {
    data class Message(val text: String) : DocumentsEffect
    data class Done(val what: Int) : DocumentsEffect
}

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val repository: DocumentsRepository,
    private val images: ProofImages,
    private val files: com.saferenttechnologies.android.domain.repository.DocumentFiles,
    sessionEvents: SessionEvents,
) : MviViewModel<DocumentsState, DocumentsIntent, DocumentsEffect>(DocumentsState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { DocumentsState() } } }
    }

    override suspend fun handleIntent(intent: DocumentsIntent) {
        when (intent) {
            DocumentsIntent.OpenMine -> {
                setState { DocumentsState(isOpen = true, loading = true) }
                loadVault(refreshing = false)
            }
            is DocumentsIntent.OpenShared -> {
                setState { DocumentsState(isOpen = true, leaseId = intent.leaseId, loading = true) }
                loadShared(refreshing = false)
            }
            // Leaving clears everything (no ID data kept in memory longer than needed).
            DocumentsIntent.Close -> back()
            DocumentsIntent.Refresh -> if (currentState.landlord) loadShared(refreshing = true) else loadVault(refreshing = true)
            DocumentsIntent.StartUpload -> setState { copy(upload = UploadForm(type = addable.singleOrNull())) }
            is DocumentsIntent.PickType -> setState { copy(upload = upload?.copy(type = intent.type, pages = upload.pages.take(intent.type.pages.coerceIn(1, DocumentRules.MAX_PAGES)))) }
            is DocumentsIntent.AddPages -> setState {
                val max = (upload?.type?.pages ?: DocumentRules.MAX_PAGES).coerceIn(1, DocumentRules.MAX_PAGES)
                copy(upload = upload?.copy(pages = (upload.pages + intent.uris).distinct().take(max), pdf = null, pdfName = ""))
            }
            is DocumentsIntent.PickPdf -> {
                val name = withContext(Dispatchers.IO) { files.displayName(intent.uri) }
                // A PDF replaces the photos (one or the other).
                setState { copy(upload = upload?.copy(pdf = intent.uri, pdfName = name, pages = emptyList())) }
            }
            DocumentsIntent.RemovePdf -> setState { copy(upload = upload?.copy(pdf = null, pdfName = "")) }
            is DocumentsIntent.RemovePage -> setState { copy(upload = upload?.copy(pages = upload.pages.filterIndexed { i, _ -> i != intent.index })) }
            DocumentsIntent.DismissUpload -> if (!currentState.busy) setState { copy(upload = null) }
            DocumentsIntent.SendUpload -> upload()
            is DocumentsIntent.ShowShare -> setState { copy(shareFor = intent.docId) }
            is DocumentsIntent.ToggleShare -> toggleShare(intent.docId, intent.leaseId)
            is DocumentsIntent.AskDelete -> setState { copy(confirmDelete = intent.docId) }
            DocumentsIntent.ConfirmDelete -> delete()
            is DocumentsIntent.View -> view(intent.doc, intent.page)
            DocumentsIntent.CloseViewer -> setState { copy(viewer = null) }
        }
    }

    private fun back() {
        val s = currentState
        when {
            s.viewer != null -> setState { copy(viewer = null) }
            s.upload != null -> if (!s.busy) setState { copy(upload = null) }
            s.shareFor != null -> setState { copy(shareFor = null) }
            else -> setState { DocumentsState() }
        }
    }

    private suspend fun loadVault(refreshing: Boolean) {
        setState { copy(refreshing = refreshing, error = null) }
        when (val r = repository.vault()) {
            is AppResult.Success -> setState { copy(vault = r.data, loading = false, refreshing = false) }
            is AppResult.Failure -> setState { copy(loading = false, refreshing = false, error = r.error.text()) }
        }
    }

    private suspend fun loadShared(refreshing: Boolean) {
        val leaseId = currentState.leaseId ?: return
        setState { copy(refreshing = refreshing, error = null) }
        when (val r = repository.shared(leaseId)) {
            is AppResult.Success -> setState { copy(shared = r.data, loading = false, refreshing = false, accessEnded = false) }
            is AppResult.Failure -> {
                val ended = (r.error as? AppError.Server)?.httpCode in setOf(403, 404, 410) ||
                    (r.error as? AppError.Server)?.errorCode.equals("LEASE_ENDED", true)
                // Lease over → nothing of the tenant's documents stays on screen.
                setState { copy(shared = null, loading = false, refreshing = false, accessEnded = ended, error = if (ended) null else r.error.text(), viewer = null) }
            }
        }
    }

    private suspend fun upload() {
        val f = currentState.upload ?: return
        val type = f.type ?: return
        if (!currentState.canUpload) return
        setState { copy(busy = true) }
        var pdf: ByteArray? = null
        val bytes: List<ByteArray>
        if (f.pdf != null) {
            when (val p = withContext(Dispatchers.IO) { files.readPdf(f.pdf) }) {
                is com.saferenttechnologies.android.domain.repository.PdfRead.Ok -> pdf = p.bytes
                com.saferenttechnologies.android.domain.repository.PdfRead.TooBig -> {
                    setState { copy(busy = false) }
                    return sendEffect(DocumentsEffect.Message(PDF_TOO_BIG))
                }
                com.saferenttechnologies.android.domain.repository.PdfRead.NotPdf -> {
                    setState { copy(busy = false) }
                    return sendEffect(DocumentsEffect.Message(PDF_UNREADABLE))
                }
            }
            bytes = emptyList()
        } else {
            val read = withContext(Dispatchers.IO) { f.pages.map { images.readJpeg(it) } }
            if (read.any { it == null }) {
                setState { copy(busy = false) }
                return sendEffect(DocumentsEffect.Message(PHOTO_UNREADABLE))
            }
            bytes = read.filterNotNull()
        }
        when (val r = repository.upload(type.key, bytes, pdf)) {
            is AppResult.Success -> {
                setState { copy(busy = false, upload = null, vault = r.data) }
                sendEffect(DocumentsEffect.Done(DONE_UPLOADED))
            }
            is AppResult.Failure -> {
                setState { copy(busy = false) }
                r.error.text()?.let { sendEffect(DocumentsEffect.Message(it)) }
            }
        }
    }

    private suspend fun toggleShare(docId: String, leaseId: String) {
        val doc = currentState.doc(docId) ?: return
        setState { copy(busy = true) }
        val r = if (doc.sharedWith(leaseId)) repository.unshare(docId, leaseId) else repository.share(docId, leaseId)
        when (r) {
            is AppResult.Success -> setState { copy(busy = false, vault = r.data) }
            is AppResult.Failure -> {
                setState { copy(busy = false) }
                r.error.text()?.let { sendEffect(DocumentsEffect.Message(it)) }
            }
        }
    }

    private suspend fun delete() {
        val id = currentState.confirmDelete ?: return
        setState { copy(busy = true) }
        when (val r = repository.delete(id)) {
            is AppResult.Success -> {
                setState { copy(busy = false, confirmDelete = null, vault = r.data) }
                sendEffect(DocumentsEffect.Done(DONE_DELETED))
            }
            is AppResult.Failure -> {
                setState { copy(busy = false, confirmDelete = null) }
                r.error.text()?.let { sendEffect(DocumentsEffect.Message(it)) }
            }
        }
    }

    /** Photos → pager; PDF → download (memory only) and render on the phone. */
    private suspend fun view(doc: TenantDoc, page: Int) {
        val pdfPage = doc.pages.firstOrNull { it.isPdf }
        if (pdfPage == null) return setState { copy(viewer = DocViewer(images = doc.pages.map { it.url }, start = page)) }
        setState { copy(viewer = DocViewer(pdfLoading = true)) }
        val bytes = repository.fileBytes(pdfPage.url)
        if (currentState.viewer == null) return // closed while loading
        if (bytes == null || !DocumentRules.looksLikePdf(bytes)) {
            setState { copy(viewer = null) }
            return sendEffect(DocumentsEffect.Message(PDF_UNREADABLE))
        }
        setState { copy(viewer = DocViewer(pdf = bytes)) }
    }

    private fun AppError.text(): String? = takeUnless { it is AppError.AccountBlocked }?.message

    companion object {
        const val PHOTO_UNREADABLE = "@photo_unreadable"
        const val PDF_UNREADABLE = "@pdf_unreadable"
        const val PDF_TOO_BIG = "@pdf_too_big"
        const val DONE_UPLOADED = 1
        const val DONE_DELETED = 2
    }
}
