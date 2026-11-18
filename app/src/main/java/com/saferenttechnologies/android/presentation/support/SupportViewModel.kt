package com.saferenttechnologies.android.presentation.support

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.PropertyFilter
import com.saferenttechnologies.android.domain.model.SupportRules
import com.saferenttechnologies.android.domain.model.SupportTab
import com.saferenttechnologies.android.domain.model.Ticket
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import com.saferenttechnologies.android.domain.repository.ManagePropertyRepository
import com.saferenttechnologies.android.domain.repository.SupportRepository
import com.saferenttechnologies.android.domain.usecase.NotificationEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SupportMode { LIST, NEW, DETAIL }

data class TicketForm(
    val category: String? = null,
    val propertyId: String? = null,
    val subject: String = "",
    val description: String = "",
    val priority: String = "MEDIUM",
    val showErrors: Boolean = false,
)

data class PropertyChoice(val id: String, val title: String)

data class SupportState(
    val isOpen: Boolean = false,
    val mode: SupportMode = SupportMode.LIST,
    val landlord: Boolean = false,
    val myId: String = "",
    /** Opened straight on a ticket / the form (push, "Report a problem") → Back closes everything. */
    val direct: Boolean = false,
    val tab: SupportTab = SupportTab.OPEN,
    val items: List<Ticket> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    // new ticket
    val form: TicketForm = TicketForm(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val properties: List<PropertyChoice> = emptyList(),
    val sending: Boolean = false,
    // detail
    val detail: Ticket? = null,
    val detailLoading: Boolean = false,
    val reply: String = "",
    val replying: Boolean = false,
    /** Bumped after a send → the chat scrolls to the bottom. */
    val scrollTick: Int = 0,
) : UiState {
    val categories: List<String> get() = SupportRules.categories(landlord)
    val canSend: Boolean get() = !sending && SupportRules.canSend(form.category, form.subject, form.description)
    val canReply: Boolean get() = !replying && detail?.closed == false && SupportRules.messageOk(reply)
    fun isMine(authorId: String, fromSupport: Boolean) = if (myId.isNotBlank()) authorId == myId else !fromSupport
}

sealed interface SupportIntent : UiIntent {
    data class Open(val landlord: Boolean, val myId: String) : SupportIntent
    /** "Report a problem": new ticket with category / property filled in. */
    data class OpenNew(val landlord: Boolean, val myId: String, val category: String? = null, val propertyId: String? = null) : SupportIntent
    data class OpenDetail(val id: String, val landlord: Boolean, val myId: String) : SupportIntent
    data object Back : SupportIntent
    data object Close : SupportIntent
    data class SelectTab(val tab: SupportTab) : SupportIntent
    data object Refresh : SupportIntent
    data object LoadMore : SupportIntent
    data object StartNew : SupportIntent
    data class Category(val key: String) : SupportIntent
    data class Property(val id: String?) : SupportIntent
    data class Subject(val text: String) : SupportIntent
    data class Description(val text: String) : SupportIntent
    data class Priority(val key: String) : SupportIntent
    data object Send : SupportIntent
    data class Select(val id: String) : SupportIntent
    data class ReplyText(val text: String) : SupportIntent
    data object SendReply : SupportIntent
}

sealed interface SupportEffect : UiEffect {
    data class Message(val text: String) : SupportEffect
}

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val repository: SupportRepository,
    private val manage: ManagePropertyRepository,
    private val browse: BrowseRepository,
    events: NotificationEvents,
    sessionEvents: SessionEvents,
) : MviViewModel<SupportState, SupportIntent, SupportEffect>(SupportState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { SupportState() } } }
        // A push for the ticket on screen ("Reply on: …", "Ticket update") → refresh it.
        viewModelScope.launch {
            events.pushes.collect { p ->
                val id = currentState.detail?.id ?: return@collect
                if (currentState.isOpen && currentState.mode == SupportMode.DETAIL && p.link.trimEnd('/').endsWith("/support/$id")) loadDetail(id, quiet = true)
            }
        }
    }

    override suspend fun handleIntent(intent: SupportIntent) {
        when (intent) {
            is SupportIntent.Open -> {
                setState { SupportState(isOpen = true, landlord = intent.landlord, myId = intent.myId, loading = true) }
                load(1, refreshing = false)
            }
            is SupportIntent.OpenNew -> {
                setState {
                    SupportState(
                        isOpen = true, landlord = intent.landlord, myId = intent.myId, mode = SupportMode.NEW, direct = true,
                        form = TicketForm(category = intent.category?.takeIf { it in SupportRules.categories(intent.landlord) }, propertyId = intent.propertyId),
                    )
                }
                loadProperties()
            }
            is SupportIntent.OpenDetail -> {
                setState { SupportState(isOpen = true, landlord = intent.landlord, myId = intent.myId, mode = SupportMode.DETAIL, direct = true) }
                loadDetail(intent.id, quiet = false)
            }
            SupportIntent.Back -> back()
            SupportIntent.Close -> setState { SupportState() }
            is SupportIntent.SelectTab -> if (intent.tab != currentState.tab) {
                setState { copy(tab = intent.tab, items = emptyList(), page = 0, hasMore = false, loading = true) }
                load(1, refreshing = false)
            }
            SupportIntent.Refresh -> {
                com.saferenttechnologies.android.data.remote.ApiCache.clear()
                when (currentState.mode) {
                    SupportMode.DETAIL -> currentState.detail?.id?.let { loadDetail(it, quiet = true) }
                    SupportMode.LIST -> load(1, refreshing = true)
                    SupportMode.NEW -> Unit
                }
            }
            SupportIntent.LoadMore -> if (currentState.hasMore && !currentState.loading) load(currentState.page + 1, refreshing = false)
            SupportIntent.StartNew -> {
                setState { copy(mode = SupportMode.NEW, form = TicketForm(), fieldErrors = emptyMap()) }
                loadProperties()
            }
            is SupportIntent.Category -> setState { copy(form = form.copy(category = intent.key)) }
            is SupportIntent.Property -> setState { copy(form = form.copy(propertyId = intent.id)) }
            is SupportIntent.Subject -> setState { copy(form = form.copy(subject = intent.text.take(SupportRules.SUBJECT_MAX)), fieldErrors = fieldErrors - "subject") }
            is SupportIntent.Description -> setState { copy(form = form.copy(description = intent.text.take(SupportRules.DESCRIPTION_MAX)), fieldErrors = fieldErrors - "description") }
            is SupportIntent.Priority -> setState { copy(form = form.copy(priority = intent.key)) }
            SupportIntent.Send -> send()
            is SupportIntent.Select -> {
                setState { copy(mode = SupportMode.DETAIL, detail = items.firstOrNull { it.id == intent.id }, reply = "") }
                loadDetail(intent.id, quiet = false)
            }
            is SupportIntent.ReplyText -> setState { copy(reply = intent.text.take(SupportRules.MESSAGE_MAX)) }
            SupportIntent.SendReply -> sendReply()
        }
    }

    private suspend fun back() {
        val s = currentState
        when {
            s.sending || s.replying -> Unit
            s.direct || s.mode == SupportMode.LIST -> setState { SupportState() }
            else -> {
                setState { copy(mode = SupportMode.LIST, detail = null, reply = "", loading = items.isEmpty()) }
                load(1, refreshing = true)
            }
        }
    }

    /** One request per status; the "Open" tab merges OPEN + IN_PROGRESS. */
    private suspend fun load(page: Int, refreshing: Boolean) {
        setState { copy(refreshing = refreshing, error = null) }
        val statuses = currentState.tab.statuses
        val results = coroutineScope { statuses.map { st -> async { repository.list(st, page) } }.map { it.await() } }
        val failure = results.firstOrNull { it is AppResult.Failure } as? AppResult.Failure
        val pages = results.mapNotNull { (it as? AppResult.Success)?.data }
        if (pages.isEmpty() && failure != null) {
            return setState { copy(loading = false, refreshing = false, error = failure.error.text()) }
        }
        val merged = SupportRules.merge(*pages.map { it.items }.toTypedArray())
        setState {
            copy(
                items = if (page == 1) merged else SupportRules.merge(items, merged),
                page = page,
                hasMore = pages.any { it.hasMore },
                loading = false,
                refreshing = false,
            )
        }
    }

    private suspend fun loadDetail(id: String, quiet: Boolean) {
        setState { copy(detailLoading = !quiet || detail == null) }
        when (val r = repository.detail(id)) {
            is AppResult.Success -> setState { copy(detail = r.data, detailLoading = false, refreshing = false) }
            is AppResult.Failure -> {
                setState { copy(detailLoading = false, refreshing = false) }
                r.error.text()?.let { sendEffect(SupportEffect.Message(it)) }
                // 404 = not mine / deleted → leave it.
                if ((r.error as? AppError.Server)?.httpCode == 404 && currentState.mode == SupportMode.DETAIL) back()
            }
        }
    }

    /** Property dropdown: landlord → my properties; tenant → my current rental. */
    private suspend fun loadProperties() {
        if (currentState.properties.isNotEmpty()) return
        val list = if (currentState.landlord) {
            (manage.list(PropertyFilter.ALL, "", 1) as? AppResult.Success)?.data?.items.orEmpty()
                .map { PropertyChoice(it.id, it.title.ifBlank { it.name.ifBlank { it.code } }) }
        } else {
            (browse.activeLease() as? AppResult.Success)?.data?.takeIf { it.propertyId.isNotBlank() }
                ?.let { listOf(PropertyChoice(it.propertyId, listOf(it.propertyTitle, it.place).filter { s -> s.isNotBlank() }.joinToString(" · "))) }
                .orEmpty()
        }
        setState { copy(properties = list) }
    }

    private suspend fun send() {
        val f = currentState.form
        val category = f.category
        if (category == null || !SupportRules.canSend(category, f.subject, f.description)) return setState { copy(form = form.copy(showErrors = true)) }
        setState { copy(sending = true, fieldErrors = emptyMap()) }
        when (val r = repository.create(category, f.subject, f.description, f.priority, f.propertyId)) {
            is AppResult.Success -> {
                // Straight to the new ticket's chat.
                setState { copy(sending = false, mode = SupportMode.DETAIL, detail = r.data, form = TicketForm(), direct = direct) }
                loadDetail(r.data.id, quiet = true)
            }
            is AppResult.Failure -> {
                val issues = (r.error as? AppError.FieldIssues)?.issues.orEmpty()
                setState { copy(sending = false, fieldErrors = issues, form = form.copy(showErrors = true)) }
                // 429 too many tickets, network … → the server's message.
                r.error.text()?.let { sendEffect(SupportEffect.Message(it)) }
            }
        }
    }

    private suspend fun sendReply() {
        val t = currentState.detail ?: return
        if (!currentState.canReply) return
        setState { copy(replying = true) }
        when (val r = repository.reply(t.id, currentState.reply)) {
            is AppResult.Success -> {
                setState { copy(reply = "", replying = false) }
                loadDetail(t.id, quiet = true)
                setState { copy(scrollTick = scrollTick + 1) }
            }
            is AppResult.Failure -> {
                setState { copy(replying = false) }
                r.error.text()?.let { sendEffect(SupportEffect.Message(it)) }
                // 412 "This ticket is closed." → reload so the reply bar goes away.
                if (r.error is AppError.InvalidState) loadDetail(t.id, quiet = true)
            }
        }
    }

    private fun AppError.text(): String? = takeUnless { it is AppError.AccountBlocked }?.message
}
