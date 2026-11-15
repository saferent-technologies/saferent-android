package com.saferenttechnologies.android.presentation.announcements

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.Announcement
import com.saferenttechnologies.android.domain.model.AnnouncementAudience
import com.saferenttechnologies.android.domain.model.AnnouncementRules
import com.saferenttechnologies.android.domain.model.AnnouncementTarget
import com.saferenttechnologies.android.domain.model.AnnouncementViewer
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.repository.AnnouncementsRepository
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository
import com.saferenttechnologies.android.domain.repository.ProofImages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class AnnouncementsMode { LIST, COMPOSE, DETAIL }

data class ComposeForm(
    val audience: AnnouncementAudience = AnnouncementAudience.ALL,
    val propertyIds: Set<String> = emptySet(),
    val leaseIds: Set<String> = emptySet(),
    val query: String = "",
    val title: String = "",
    val body: String = "",
    /** Picked / taken image uri. */
    val image: String? = null,
    val showErrors: Boolean = false,
)

data class AnnouncementsState(
    val isOpen: Boolean = false,
    val mode: AnnouncementsMode = AnnouncementsMode.LIST,
    val landlord: Boolean = false,
    /** Opened straight on one (push) → Back closes everything. */
    val direct: Boolean = false,
    val items: List<Announcement> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    // compose
    val form: ComposeForm = ComposeForm(),
    val properties: List<AnnouncementTarget> = emptyList(),
    val tenants: List<AnnouncementTarget> = emptyList(),
    val targetsLoading: Boolean = false,
    val sending: Boolean = false,
    // detail
    val detail: Announcement? = null,
    val detailLoading: Boolean = false,
    val viewers: List<AnnouncementViewer> = emptyList(),
    val viewersLoading: Boolean = false,
    /** Landlord detail: Seen (true) / Not seen (false). */
    val seenTab: Boolean = true,
    val imageViewer: String? = null,
) : UiState {
    val hasMore: Boolean get() = page < totalPages
    val canSend: Boolean
        get() = !sending && AnnouncementRules.canSend(form.title, form.body, form.audience, form.propertyIds, form.leaseIds)
    /** Rows for the current picker (properties / tenants), filtered by the search box. */
    val pickerRows: List<AnnouncementTarget>
        get() = AnnouncementRules.search(if (form.audience == AnnouncementAudience.PROPERTIES) properties else tenants, form.query)
    val seen: List<AnnouncementViewer> get() = viewers.filter { it.seen }
    val notSeen: List<AnnouncementViewer> get() = viewers.filterNot { it.seen }
}

sealed interface AnnouncementsIntent : UiIntent {
    data class Open(val landlord: Boolean) : AnnouncementsIntent
    data class OpenDetail(val id: String, val landlord: Boolean) : AnnouncementsIntent
    data object Back : AnnouncementsIntent
    data object Refresh : AnnouncementsIntent
    data object LoadMore : AnnouncementsIntent
    data object StartCompose : AnnouncementsIntent
    data class Audience(val audience: AnnouncementAudience) : AnnouncementsIntent
    data class Toggle(val id: String) : AnnouncementsIntent
    data object SelectAllShown : AnnouncementsIntent
    data class Query(val text: String) : AnnouncementsIntent
    data class Title(val text: String) : AnnouncementsIntent
    data class Body(val text: String) : AnnouncementsIntent
    data class Image(val uri: String?) : AnnouncementsIntent
    data object Send : AnnouncementsIntent
    data class Select(val id: String) : AnnouncementsIntent
    data class SeenTab(val seen: Boolean) : AnnouncementsIntent
    data class ViewImage(val url: String?) : AnnouncementsIntent
}

sealed interface AnnouncementsEffect : UiEffect {
    data class Message(val text: String) : AnnouncementsEffect
    data object Sent : AnnouncementsEffect
}

@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val repository: AnnouncementsRepository,
    private val leases: ManagePropertyRepository,
    private val images: ProofImages,
    sessionEvents: SessionEvents,
) : MviViewModel<AnnouncementsState, AnnouncementsIntent, AnnouncementsEffect>(AnnouncementsState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { AnnouncementsState() } } }
    }

    override suspend fun handleIntent(intent: AnnouncementsIntent) {
        when (intent) {
            is AnnouncementsIntent.Open -> {
                setState { AnnouncementsState(isOpen = true, landlord = intent.landlord) }
                load(1, refreshing = false)
            }
            is AnnouncementsIntent.OpenDetail -> {
                setState { AnnouncementsState(isOpen = true, landlord = intent.landlord, mode = AnnouncementsMode.DETAIL, direct = true) }
                loadDetail(intent.id)
            }
            AnnouncementsIntent.Back -> back()
            AnnouncementsIntent.Refresh -> when (currentState.mode) {
                AnnouncementsMode.DETAIL -> currentState.detail?.id?.let { loadDetail(it) }
                AnnouncementsMode.LIST -> load(1, refreshing = true)
                AnnouncementsMode.COMPOSE -> Unit
            }
            AnnouncementsIntent.LoadMore -> if (currentState.hasMore && !currentState.loading) load(currentState.page + 1, refreshing = false)
            AnnouncementsIntent.StartCompose -> {
                setState { copy(mode = AnnouncementsMode.COMPOSE, form = ComposeForm()) }
                loadTargets()
            }
            is AnnouncementsIntent.Audience -> setState { copy(form = form.copy(audience = intent.audience, query = "")) }
            is AnnouncementsIntent.Toggle -> setState {
                copy(
                    form = if (form.audience == AnnouncementAudience.PROPERTIES) form.copy(propertyIds = form.propertyIds.toggle(intent.id))
                    else form.copy(leaseIds = form.leaseIds.toggle(intent.id)),
                )
            }
            AnnouncementsIntent.SelectAllShown -> setState {
                val ids = pickerRows.map { it.id }.toSet()
                if (form.audience == AnnouncementAudience.PROPERTIES) {
                    copy(form = form.copy(propertyIds = if (form.propertyIds.containsAll(ids)) form.propertyIds - ids else form.propertyIds + ids))
                } else {
                    copy(form = form.copy(leaseIds = if (form.leaseIds.containsAll(ids)) form.leaseIds - ids else form.leaseIds + ids))
                }
            }
            is AnnouncementsIntent.Query -> setState { copy(form = form.copy(query = intent.text.take(60))) }
            is AnnouncementsIntent.Title -> setState { copy(form = form.copy(title = intent.text.take(AnnouncementRules.TITLE_MAX))) }
            is AnnouncementsIntent.Body -> setState { copy(form = form.copy(body = intent.text.take(AnnouncementRules.BODY_MAX))) }
            is AnnouncementsIntent.Image -> setState { copy(form = form.copy(image = intent.uri)) }
            AnnouncementsIntent.Send -> send()
            is AnnouncementsIntent.Select -> {
                setState { copy(mode = AnnouncementsMode.DETAIL, detail = items.firstOrNull { it.id == intent.id }, viewers = emptyList(), seenTab = true) }
                loadDetail(intent.id)
            }
            is AnnouncementsIntent.SeenTab -> setState { copy(seenTab = intent.seen) }
            is AnnouncementsIntent.ViewImage -> setState { copy(imageViewer = intent.url) }
        }
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    private suspend fun back() {
        val s = currentState
        when {
            s.imageViewer != null -> setState { copy(imageViewer = null) }
            s.sending -> Unit
            s.direct || s.mode == AnnouncementsMode.LIST -> setState { AnnouncementsState() }
            else -> {
                setState { copy(mode = AnnouncementsMode.LIST, detail = null, viewers = emptyList()) }
                load(1, refreshing = true)
            }
        }
    }

    private suspend fun load(page: Int, refreshing: Boolean) {
        setState { copy(loading = !refreshing, refreshing = refreshing, error = null) }
        when (val r = repository.list(currentState.landlord, page)) {
            is AppResult.Success -> setState {
                copy(
                    items = if (page == 1) r.data.items else (items + r.data.items).distinctBy { it.id },
                    page = r.data.page,
                    totalPages = r.data.totalPages,
                    loading = false,
                    refreshing = false,
                )
            }
            is AppResult.Failure -> setState { copy(loading = false, refreshing = false, error = r.error.text()) }
        }
    }

    private suspend fun loadDetail(id: String) {
        setState { copy(detailLoading = true) }
        when (val r = repository.detail(id)) {
            is AppResult.Success -> {
                setState { copy(detail = r.data, detailLoading = false) }
                if (currentState.landlord) loadViewers(id) else markViewed(r.data)
            }
            is AppResult.Failure -> {
                setState { copy(detailLoading = false) }
                r.error.text()?.let { sendEffect(AnnouncementsEffect.Message(it)) }
                if (currentState.detail == null) back()
            }
        }
    }

    /** Tenant opened it → tell the server (once; quietly, failures don't bother the tenant). */
    private suspend fun markViewed(a: Announcement) {
        if (a.viewed) return
        if (repository.markViewed(a.id) is AppResult.Success) {
            setState { copy(detail = detail?.copy(viewed = true), items = items.map { if (it.id == a.id) it.copy(viewed = true) else it }) }
        }
    }

    private suspend fun loadViewers(id: String) {
        setState { copy(viewersLoading = true) }
        when (val r = repository.recipients(id)) {
            is AppResult.Success -> setState { copy(viewers = r.data, viewersLoading = false) }
            is AppResult.Failure -> setState { copy(viewersLoading = false) }
        }
    }

    private suspend fun loadTargets() {
        if (currentState.tenants.isNotEmpty()) return
        setState { copy(targetsLoading = true) }
        when (val r = leases.allLeases(active = true)) {
            is AppResult.Success -> setState {
                copy(properties = AnnouncementRules.properties(r.data), tenants = AnnouncementRules.tenants(r.data), targetsLoading = false)
            }
            is AppResult.Failure -> {
                setState { copy(targetsLoading = false) }
                r.error.text()?.let { sendEffect(AnnouncementsEffect.Message(it)) }
            }
        }
    }

    private suspend fun send() {
        val s = currentState
        val f = s.form
        if (!AnnouncementRules.canSend(f.title, f.body, f.audience, f.propertyIds, f.leaseIds)) return setState { copy(form = form.copy(showErrors = true)) }
        setState { copy(sending = true) }
        val image = f.image?.let { uri ->
            withContext(Dispatchers.IO) { images.readJpeg(uri) } ?: run {
                setState { copy(sending = false) }
                return sendEffect(AnnouncementsEffect.Message(PHOTO_UNREADABLE))
            }
        }
        when (val r = repository.send(f.title, f.body, f.audience, f.propertyIds, f.leaseIds, image)) {
            is AppResult.Success -> {
                setState { copy(sending = false, form = ComposeForm(), mode = AnnouncementsMode.DETAIL, detail = r.data, viewers = emptyList(), seenTab = true) }
                sendEffect(AnnouncementsEffect.Sent)
                loadViewers(r.data.id)
            }
            is AppResult.Failure -> {
                setState { copy(sending = false) }
                r.error.text()?.let { sendEffect(AnnouncementsEffect.Message(it)) }
            }
        }
    }

    private fun AppError.text(): String? = takeUnless { it is AppError.AccountBlocked }?.message

    companion object {
        const val PHOTO_UNREADABLE = "@photo_unreadable"
    }
}
